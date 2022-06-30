package com.rootsid.wal.library.wallet.storage

import com.rootsid.wal.library.wallet.model.BlockchainTxLog

interface BlockchainTxLogStorage {
    fun insert(txLog: BlockchainTxLog): BlockchainTxLog

    fun list(): List<BlockchainTxLog>
}
