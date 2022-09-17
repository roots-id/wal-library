package com.rootsid.wal.library.wallet

import com.rootsid.wal.library.Constant
import com.rootsid.wal.library.dlt.Dlt
import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.wallet.model.*
import com.rootsid.wal.library.wallet.storage.BlockchainTxLogStorage
import com.rootsid.wal.library.wallet.storage.WalletStorage
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import io.iohk.atala.prism.api.node.PrismDidState
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.crypto.derivation.MnemonicCode
import io.iohk.atala.prism.crypto.util.BytesOps
import io.iohk.atala.prism.protos.DIDData
import kotlinx.serialization.json.JsonObject
import java.util.*

class WalletService(private val walletStorage: WalletStorage, private val txLogStorage: BlockchainTxLogStorage, private val dlt: Dlt) {
    /**
     * New wallet
     *
     * @param id Wallet name
     * @param mnemonic mnemonic, leave empty to generate a random one
     * @param passphrase passphrase
     * @return a new wallet
     */
    fun createWallet(id: String, mnemonic: String, passphrase: String): Wallet {
        if (walletStorage.exists(id) || txLogStorage.exists(id)) {
            throw RuntimeException("Duplicated Wallet identifier")
        }

        val seed = this.generateSeed(mnemonic, passphrase)
        return walletStorage.insert(walletStorage.createWalletObject(id, BytesOps.bytesToHex(seed)))
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
        try {
            val mnemonicList =
                if (mnemonic.trim().isBlank()) MnemonicCode(KeyDerivation.randomMnemonicCode().words) else MnemonicCode(
                    mnemonic.split(Constant.MNEMONIC_SEPARATOR).map { it.trim() })

            return KeyDerivation.binarySeed(mnemonicList, passphrase)
        } catch (e: Exception) {
            throw RuntimeException("Invalid mnemonic phrase")
        }
    }

    /**
     * Create new did
     *
     * @param walletId Name of the wallet.
     * @param didAlias Name of the alias, by default is random UUID.
     * @param issuer Add issuing and revocation keys, by default is false.
     */
    fun createDid(walletId: String, didAlias: String = UUID.randomUUID().toString(), issuer: Boolean = false): Did {
        this.findWalletById(walletId)
            .let { wallet ->
                if (wallet.dids.any { it.alias.equals(didAlias, true) }) {
                    throw RuntimeException("Duplicated DID alias")
                }

                val newDid = dlt.newDid(didAlias, wallet.dids.size, BytesOps.hexToBytes(wallet.seed), issuer)
                wallet.addDid(newDid)

                walletStorage.update(wallet)
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
        this.findWalletById(walletId)
            .let { wallet ->
                wallet.dids.firstOrNull { it.alias.equals(didAlias, true) }
                    ?.let { did ->
                        val didUpdate = dlt.publishDid(did, BytesOps.hexToBytes(wallet.seed))

                        //did.publishedStatus = AtalaOperationStatus.PENDING_SUBMISSION
                        did.operationHash =
                            didUpdate.operationHash.ifBlank { throw RuntimeException("Unable to find operation id.") }
                        did.operationId =
                            didUpdate.operationId.ifBlank { throw RuntimeException("Unable to find operation id.") }

                        walletStorage.update(wallet)
                        txLogStorage.insert(
                            txLogStorage.createTxLogObject(
                                did.operationId.toString(),
                                walletId,
                                BlockchainTxAction.PUBLISH_DID,
                                "Publish DID: $didAlias"
                            )
                        )
                        println("DID '$didAlias' published.")

                        return did
                    }
                    ?: throw RuntimeException("Did alias '$didAlias' not found")
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
        return this.getDidPublishOperationStatus(findWalletById(walletId), didAlias)
    }

    fun getDidLastOperationStatus(wallet: Wallet, didAlias: String): AtalaOperationStatusEnum {
        wallet.dids.firstOrNull { it.alias.equals(didAlias, true) }
            ?.let { did ->
                val publishOperationInfo = dlt.getDidLastOperationInfo(did)
                // did.publishedStatus = publishOperationInfo.status
                return publishOperationInfo.status
            } ?: throw RuntimeException("Did alias '$didAlias' not found")
    }

    fun updateTxLogOperationInfo(operationId: String) {
        // TODO: update txLog operation info
    }
}
