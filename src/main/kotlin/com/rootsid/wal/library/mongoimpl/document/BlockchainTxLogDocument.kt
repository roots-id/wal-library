package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.utils.LocalDateTimeSerializer
import com.rootsid.wal.library.wallet.model.BlockchainTxAction
import com.rootsid.wal.library.wallet.model.BlockchainTxLog
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Blockchain tx log document
 *
 * @property _id - unique id
 * @property walletId - Id of the wallet associated with the record
 * @property action - action performed on the wallet
 * @property description - description of the action
 * @property createdAt - date and time of the action
 * @property updatedAt - date and time of the last update
 * @property status - status of the action
 * @property txId - blockchain transaction id
 * @property url - url of the bloackchain transaction
 * @constructor Create empty Blockchain tx log document
 */
data class BlockchainTxLogDocument(
    override val _id: String,
    override val walletId: String,
    override val action: BlockchainTxAction,
    override var description: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
    override var status: AtalaOperationStatusEnum = AtalaOperationStatus.PENDING_SUBMISSION,
    override var txId: String? = null,
    override var url: String? = null
) : BlockchainTxLog
