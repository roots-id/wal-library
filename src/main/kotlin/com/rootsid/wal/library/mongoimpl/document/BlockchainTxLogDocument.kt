package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.wallet.model.BlockchainTxAction
import com.rootsid.wal.library.wallet.model.BlockchainTxLog
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import kotlinx.serialization.Serializable

@Serializable
data class BlockchainTxLogDocument(
    override val _id: String,
    override val walletId: String,
    override val action: BlockchainTxAction,
    override var description: String? = null,
    override var status: AtalaOperationStatusEnum = AtalaOperationStatus.PENDING_SUBMISSION,
    override var txId: String? = null,
    override var url: String? = null
    // val walletId: String
    // override var logEntries: MutableList<BlockchainTxLogEntry> = mutableListOf()
) : BlockchainTxLog