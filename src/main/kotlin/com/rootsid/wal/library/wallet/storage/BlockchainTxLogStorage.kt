package com.rootsid.wal.library.wallet.storage

import com.rootsid.wal.library.wallet.model.BlockchainTxAction
import com.rootsid.wal.library.wallet.model.BlockchainTxLog
import java.util.*

interface BlockchainTxLogStorage {

    // Use same id as the Wallet
    fun createTxLogObject(txLogId: String, walletId: String, action: BlockchainTxAction, description: String?): BlockchainTxLog

    fun insert(txLog: BlockchainTxLog): BlockchainTxLog

    fun list(): List<BlockchainTxLog>

    fun update(txLog: BlockchainTxLog): Boolean

    fun findById(txLogId: String): BlockchainTxLog

    fun exists(txLogId: String): Boolean

}
