package com.rootsid.wal.library.wallet

import com.rootsid.wal.library.Constant
import com.rootsid.wal.library.dlt.Dlt
import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.mongoimpl.document.WalletDocument
import com.rootsid.wal.library.wallet.model.Wallet
import com.rootsid.wal.library.wallet.model.addDid
import com.rootsid.wal.library.wallet.storage.WalletStorage
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import io.iohk.atala.prism.api.node.PrismDidState
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.crypto.derivation.MnemonicCode
import io.iohk.atala.prism.crypto.util.BytesOps
import io.iohk.atala.prism.protos.DIDData
import kotlinx.serialization.json.JsonObject
import java.util.*

class WalletService(private val walletStorage: WalletStorage, private val dlt: Dlt) {

    /**
     * New wallet
     *
     * @param id Wallet name
     * @param mnemonic mnemonic, leave empty to generate a random one
     * @param passphrase passphrase
     * @return a new wallet
     */
    fun createWallet(id: String, mnemonic: String, passphrase: String): Wallet {
        val seed = generateSeed(mnemonic, passphrase)
        return walletStorage.insert(WalletDocument(id, BytesOps.bytesToHex(seed)))
    }

    /**
     * List wallets
     */
    fun listWallets(): List<Wallet> {
        return walletStorage.list()
    }

    /**
     * Get wallet by id
     *
     * @param walletId Name of the wallet.
     */
    fun findWalletById(walletId: String): Wallet {
        return walletStorage.findById(walletId)
    }

    /**
     * Generate random mnemonic
     *
     * @return mnemonic
     */
    fun generateMnemonic(): String {
        return KeyDerivation.randomMnemonicCode().words.joinToString(Constant.MNEMONIC_SEPARATOR)
    }

    /**
     * Generate seed
     *
     * @param mnemonic Mnemonic phrase, separated by Config.MNEMONIC_SEPARATOR. If empty, a random one will be generated
     * @param passphrase Passphrase to protect the seed with a password
     * @return seed
     */
    private fun generateSeed(mnemonic: String, passphrase: String): ByteArray {
        val seed: ByteArray
        val mnemonicList: MnemonicCode
        if (mnemonic.trim().isBlank()) {
            mnemonicList = MnemonicCode(KeyDerivation.randomMnemonicCode().words)
            seed = KeyDerivation.binarySeed(mnemonicList, passphrase)
        } else {
            try {
                mnemonicList = MnemonicCode(mnemonic.split(Constant.MNEMONIC_SEPARATOR).map { it.trim() })
                seed = KeyDerivation.binarySeed(mnemonicList, passphrase)
            } catch (e: Exception) {
                throw Exception("Invalid mnemonic phrase")
            }
        }
        return seed
    }

    /**
     * Create new did
     *
     * @param walletId Name of the wallet.
     * @param didAlias Name of the alias, by default is random UUID.
     * @param issuer Add issuing and revocation keys, by default is false.
     */
    fun createDid(walletId: String, didAlias: String = UUID.randomUUID().toString(), issuer: Boolean = false): Did {
        findWalletById(walletId)
            .let { w ->
                if (w.dids.any { it.alias.equals(didAlias, true) }) {
                    throw Exception("Duplicated DID alias")
                }

                val newDid = dlt.newDid(didAlias, w.dids.size, BytesOps.hexToBytes(w.seed), issuer)
                w.addDid(newDid)

                walletStorage.update(w)
                println("DID created")

                return newDid
            }
    }

    fun listDids(walletId: String): List<Did> {
        return walletStorage.listDids(walletId)
    }

    fun findDid(walletId: String, didAlias: String): Did {
        return walletStorage.findDidByAlias(walletId, didAlias).orElseThrow { Exception("Unable to find DID alias.") }
    }

    fun didAliasExists(walletId: String, didAlias: String): Boolean {
        return walletStorage.findDidByAlias(walletId, didAlias).isPresent
    }

    /**
     * Publish did
     *
     * @param walletId Name of the wallet.
     * @param didAlias Name of the alias, by default is random UUID.
     */
    fun publishDid(walletId: String, didAlias: String): Did {
        findWalletById(walletId)
            .let { w ->
                w.dids.firstOrNull { it.alias.equals(didAlias, true) }
                    ?.let { d ->
                        val dltDidUpdate = dlt.publishDid(d, BytesOps.hexToBytes(w.seed))

                        d.publishedStatus = AtalaOperationStatus.PENDING_SUBMISSION
                        d.publishedOperationId = dltDidUpdate.operationId ?: throw Exception("Unable to find operation id.")
                        d.operationHash = dltDidUpdate.did?.operationHash ?: throw Exception("Unable to find operation hash.")

                        walletStorage.update(w)
                        println("DID '$didAlias' published.")

                        return d
                    }
                    ?: throw Exception("Did alias '$didAlias' not found")
            }
    }

    @OptIn(PrismSdkInternal::class)
    fun getDidDocument(didUri: String): DIDData {
        return dlt.getDidDocument(didUri)
    }

    fun getDidDocumentJson(didUri: String): String {
        return dlt.getDidDocumentJson(didUri)
    }

    fun getDidDocumentStatus(didUri: String): PrismDidState {
        return dlt.getDidState(didUri)
    }

    fun getDidDocumentW3C(didUri: String): JsonObject {
        return dlt.getDidDocumentW3C(didUri)
    }

    fun getDidPublishOperationStatus(walletId: String, didAlias: String): AtalaOperationStatusEnum {
        return getDidPublishOperationStatus(findWalletById(walletId), didAlias)
    }

    fun getDidPublishOperationStatus(wallet: Wallet, didAlias: String): AtalaOperationStatusEnum {
        wallet.dids.firstOrNull { it.alias.equals(didAlias, true) }
            ?.let { d ->
                val publishOperationInfo = dlt.getDidPublishOperationInfo(d)
                d.publishedStatus = publishOperationInfo
                return publishOperationInfo
            } ?: throw Exception("Did alias '$didAlias' not found")
    }
}
