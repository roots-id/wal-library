package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.prism.model.Did
import kotlinx.serialization.Serializable

/**
 * Wallet
 *
 * @property _id
 * @property mnemonic
 * @property passphrase
 * @property dids
 * @property importedCredentials
 * @property issuedCredentials
 * @constructor Create empty Wallet
 */
@Serializable
data class Wallet(
    var _id: String, // name
    val mnemonic: List<String>,
    val passphrase: String,
    val seed: String,
    var dids: MutableList<Did> = mutableListOf(),
    // List of imported (Issued elsewhere)
    var importedCredentials: MutableList<ImportedCredential> = mutableListOf(),
    // List of credentials issued by a DID from this wallet
    var issuedCredentials: MutableList<IssuedCredential> = mutableListOf(),
    var blockchainTxLogEntry: MutableList<BlockchainTxLogEntry> = mutableListOf()
)

/**
 * Add blockchain tx log
 *
 * @param entry blockchain transaction log entry
 */
fun Wallet.addBlockchainTxLog(entry: BlockchainTxLogEntry) {
    blockchainTxLogEntry.add(entry)
}
