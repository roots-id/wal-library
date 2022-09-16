package com.rootsid.wal.library.wallet.model

import kotlinx.serialization.Serializable

/**
 * Blockchain tx log
 *
 * @property txId transaction id
 * @property action one of ADD_KEY, REVOKE_KEY, PUBLISH_DID, ISSUE_CREDENTIAL, REVOKE_CREDENTIAL
 * @property url to open the transaction on a blockchain explorer
 * @constructor Create empty Blockchain tx log
 */
//@Serializable
//data class BlockchainTxLogEntry(
//    val operationId: String,
//    val action: BlockchainTxAction,
//    var description: String?,
//    var txId: String? = "",
//    var url: String? = ""
//)

// Enum for blockchain tx actions
enum class BlockchainTxAction {
    ADD_KEY,
    REVOKE_KEY,
    PUBLISH_DID,
    ISSUE_CREDENTIAL,
    REVOKE_CREDENTIAL
}
