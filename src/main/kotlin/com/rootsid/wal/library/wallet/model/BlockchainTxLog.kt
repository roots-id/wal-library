package com.rootsid.wal.library.wallet.model

import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum

interface BlockchainTxLog {
    // use operationId as the primary key
    val _id: String
    val walletId: String

    // var logEntries: MutableList<BlockchainTxLogEntry>
    val action: BlockchainTxAction
    var status: AtalaOperationStatusEnum
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
