package com.rootsid.wal.library.wallet.model

import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import java.io.Serializable

interface BlockchainTxLog : Serializable {
    // use operationId as the primary key
    val _id: String
    val walletId: String
    val action: BlockchainTxAction
    var status: AtalaOperationStatusEnum
    val createdAt: String
    var updatedAt: String
    var description: String?
    var txId: String?
    var url: String?
}

/**
 * Add blockchain tx log
 *
 * @param entry blockchain transaction log entry
 */
// fun BlockchainTxLog.addBlockchainTxLog(entry: BlockchainTxLogEntry) {
//    logEntries.add(entry)
// }
