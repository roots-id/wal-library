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
     * @param id - Wallet is identified by a unique id
     * @param mnemonic - mnemonic code to create the wallet seed. Leave empty to generate a random one
     * @param passphrase - passphrase
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
     *
     * @return List<Wallet> - List of wallets
     */
    fun listWallets(): List<Wallet> {
        return walletStorage.list()
    }

    /**
     * Find wallet by id
     *
     * @param walletId Name of the wallet.
     * @return Wallet - Wallet object
     */
    fun findWalletById(walletId: String): Wallet {
        return walletStorage.findById(walletId)
    }

    /**
     * Generate random mnemonic
     *
     * @return String - mnemonic
     */
    fun generateMnemonic(): String {
        return KeyDerivation.randomMnemonicCode().words.joinToString(Constant.MNEMONIC_SEPARATOR)
    }

    /**
     * Generate seed
     *
     * @param mnemonic - Mnemonic phrase, separated by Config.MNEMONIC_SEPARATOR. If empty, a random one will be generated
     * @param passphrase - Passphrase to protect the seed with a password
     * @return ByteArray - Seed
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
     * @return Did - Did object
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

    /**
     * List dids
     *
     * @param walletId - Identifier of the wallet.
     * @return List<Did> - List of dids
     */
    fun listDids(walletId: String): List<Did> {
        return walletStorage.listDids(walletId)
    }

    /**
     * Find did
     *
     * @param walletId - Identifier of the wallet.
     * @param didAlias - Alias of the did.
     * @return Did - Did object
     */
    fun findDid(walletId: String, didAlias: String): Did {
        return walletStorage.findDidByAlias(walletId, didAlias).orElseThrow { Exception("Unable to find DID alias.") }
    }

    /**
     * Did alias exists
     *
     * @param walletId - Identifier of the wallet.
     * @param didAlias - Alias of the did.
     * @return Boolean - True if exists, false otherwise
     */
    fun didAliasExists(walletId: String, didAlias: String): Boolean {
        return walletStorage.findDidByAlias(walletId, didAlias).isPresent
    }

    /**
     * Publish did
     *
     * @param walletId - Identifier of the wallet.
     * @param didAlias - Alias of the did.
     * @return Did - Did object
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

    /**
     * Get did document json. Returns the did document in json format.
     *
     * @param didUri
     * @return
     */
    fun getDidDocumentJson(didUri: String): String {
        return dlt.getDidDocumentJson(didUri)
    }

    /**
     * Get did document status. Returns the did document status.
     *
     * @param didUri - DID URI
     * @return PrismDidState - DID state
     */
    fun getDidDocumentStatus(didUri: String): PrismDidState {
        return dlt.getDidState(didUri)
    }

    /**
     * Get did document w3c. This method is used to get the did document in w3c format.
     *
     * @param didUri
     * @return JsonObject DidDocument
     */
    fun getDidDocumentW3C(didUri: String): JsonObject {
        return dlt.getDidDocumentW3C(didUri)
    }

    /**
     * Issue credential. This method will create a new credential and publish it to the blockchain.
     *
     * @param walletId - Identifier of the wallet.
     * @param didAlias - Alias of the did.
     * @param holderUri - Holder URI.
     * @param credentialAlias - Alias of the credential.
     * @param content - Credential content.
     * @return Credential - Credential object
     */
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

    /**
     * Revoke credential. This method will revoke the credential and update the status of the credential.
     *
     * @param walletId - Identifier of the wallet.
     * @param credentialAlias - Alias of the credential.
     * @return Credential - Credential object
     */
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

    /**
     * Verify credential. This method will verify the credential with the prism node
     *
     * @param walletId - Identifier of the wallet.
     * @param credentialAlias - Alias of the credential.
     * @return List<String> - List of errors
     */
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

    /**
     * Get did last operation status. This method will get the last operation status of the did.
     *
     * @param walletId - Identifier of the wallet.
     * @param didAlias - Alias of the did.
     * @return AtalaOperationStatus - Operation status
     */
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

    /**
     * Refresh tx log operation info. This method will refresh the operation info of the given operation id in the log
     *
     * @param operationId - Operation id to refresh.
     * @return BlockchainTxLog - Blockchain tx log
     */
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
     * Refresh tx logs - Updates the status of all operations that are in [AtalaOperationStatus.PENDING_SUBMISSION] or [AtalaOperationStatus.PENDING_CONFIRMATION]
     *
     */
    fun refreshTxLogs() {
        txLogStorage.listPending().forEach() {
            println("Refreshing operation ${it._id}")
            refreshTxLogOperationInfo(it._id)
        }
    }
}
