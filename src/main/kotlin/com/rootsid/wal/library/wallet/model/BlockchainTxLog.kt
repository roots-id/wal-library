package com.rootsid.wal.library.wallet.model

interface BlockchainTxLog {
    val _id: String
    val walletId: String
    var logEntries: MutableList<BlockchainTxLogEntry>
}

/**
 * Add blockchain tx log
 *
 * @param entry blockchain transaction log entry
 */
fun BlockchainTxLog.addBlockchainTxLog(entry: BlockchainTxLogEntry) {
    logEntries.add(entry)
}
