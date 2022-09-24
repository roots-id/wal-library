package com.rootsid.wal.library.wallet

import com.rootsid.wal.library.Constant
import com.rootsid.wal.library.dlt.Dlt
import com.rootsid.wal.library.dlt.model.Claim
import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.dlt.model.Proof
import com.rootsid.wal.library.dlt.model.VerifiedCredential
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
import java.time.LocalDateTime
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
        if (walletStorage.exists(id)) {
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
                    mnemonic.split(Constant.MNEMONIC_SEPARATOR).map { it.trim() }
                )

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

                        // did.publishedStatus = AtalaOperationStatus.PENDING_SUBMISSION
                        did.operationHash =
                            didUpdate.operationHash.ifEmpty { throw RuntimeException("Unable to find operation hash.") }
                        did.operationId =
                            didUpdate.operationId.ifEmpty { throw RuntimeException("Unable to find operation id.") }

                        walletStorage.update(wallet)
                        txLogStorage.insert(
                            txLogStorage.createTxLogObject(
                                did.operationId.last().toString(),
                                walletId,
                                BlockchainTxAction.PUBLISH_DID,
                                "Publish DID: $didAlias"
                            )
                        )
                        println("DID '$didAlias' publish operation added.")

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

//    fun getDidPublishOperationStatus(walletId: String, didAlias: String): AtalaOperationStatusEnum {
//        return this.getDidLastOperationStatus(findWalletById(walletId), didAlias)
//    }

    fun issueCredential(walletId: String, didAlias: String, holderUri: String, credentialAlias: String, content: String): IssuedCredential {
        this.findWalletById(walletId)
            .let { wallet ->
                wallet.dids.firstOrNull { it.alias.equals(didAlias, true) }
                    ?.let { did ->
                        var issuedCredential = IssuedCredential(
                            credentialAlias,
                            didAlias,
                            Claim(holderUri, content),
                            VerifiedCredential("", Proof("", 0, mutableListOf())),
                            "",
                            mutableListOf(),
                            "",
                            ""
                        )
                        dlt.issueCredential(did, BytesOps.hexToBytes(wallet.seed), issuedCredential)
                        wallet.issuedCredentials.add(issuedCredential)
                        walletStorage.update(wallet)

                        txLogStorage.insert(
                            txLogStorage.createTxLogObject(
                                did.operationId.last().toString(),
                                walletId,
                                BlockchainTxAction.ISSUE_CREDENTIAL,
                                "Issue Credential: $credentialAlias"
                            )
                        )
                        return issuedCredential
                    }
                    ?: throw RuntimeException("Did alias '$didAlias' not found")
            }
    }

    fun revokeCredential(walletId: String, credentialAlias: String): IssuedCredential {
        this.findWalletById(walletId)
            .let { wallet ->
                wallet.issuedCredentials.firstOrNull { it.alias.equals(credentialAlias, true) }
                    ?.let { credential ->
                        wallet.dids.firstOrNull { it.alias.equals(credential.issuingDidAlias, true) }
                            ?.let { did ->
                                dlt.revokeCredential(credential, did, BytesOps.hexToBytes(wallet.seed))
                                walletStorage.update(wallet)
                                txLogStorage.insert(
                                    txLogStorage.createTxLogObject(
                                        did.operationId.last().toString(),
                                        walletId,
                                        BlockchainTxAction.REVOKE_CREDENTIAL,
                                        "Revoke Credential: $credentialAlias"
                                    )
                                )
                                return credential
                            }
                            ?: throw RuntimeException("Did alias '${credential.issuingDidAlias}' not found")
                    }
                    ?: throw RuntimeException("Credential alias '$credentialAlias' not found")
            }
    }

    fun verifyCredential(walletId: String, credentialAlias: String): List<String> {
        this.findWalletById(walletId)
            .let { wallet ->
                wallet.issuedCredentials.firstOrNull { it.alias.equals(credentialAlias, true) }
                    ?.let { credential ->
                        return dlt.verifyCredential(credential)
                    }
                    ?: throw RuntimeException("Credential alias '$credentialAlias' not found")
            }
    }

    fun getDidLastOperationStatus(walletId: String, didAlias: String): AtalaOperationStatusEnum {
        this.findWalletById(walletId)
            .let { wallet ->
                wallet.dids.firstOrNull { it.alias.equals(didAlias, true) }
                    ?.let { did ->
                        if (did.operationId.isEmpty()) {
                            throw Exception("Unable to find operation information because operation id was empty.")
                        }
                        return dlt.getOperationInfo(did.operationId.last()).status
                    } ?: throw RuntimeException("Did alias '$didAlias' not found")
            }
    }

    private fun refreshTxLogOperationInfo(operationId: String): BlockchainTxLog {
        val txLog = txLogStorage.findById(operationId)
        val operationInfo = dlt.getOperationInfo(operationId)

        txLog.status = operationInfo.status
        txLog.txId = operationInfo.transactionId
        txLog.url = Constant.TESTNET_URL + operationInfo.transactionId
        txLog.updatedAt = LocalDateTime.now()
        txLogStorage.update(txLog)

        return txLog
    }

    /**
     * Refresh tx logs
     * Updates the status of all operations that are in [AtalaOperationStatus.PENDING_SUBMISSION] or [AtalaOperationStatus.PENDING_CONFIRMATION]
     * Status
     */
    fun refreshTxLogs() {
        txLogStorage.listPending().forEach() {
            println("Refreshing operation ${it._id}")
            refreshTxLogOperationInfo(it._id)
        }
    }
}
