package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.BlockchainTxLogDocument
import com.rootsid.wal.library.wallet.model.BlockchainTxAction
import com.rootsid.wal.library.wallet.model.BlockchainTxLog
import com.rootsid.wal.library.wallet.storage.BlockchainTxLogStorage
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import org.litote.kmongo.*
import java.time.LocalDateTime
import java.util.*

class BlockchainTxLogDocStorage(db: MongoDatabase? = null, collectionName: String = "tx_logs") : BlockchainTxLogStorage {
    private val txLogCollection: MongoCollection<BlockchainTxLogDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.txLogCollection = mongoConn.getCollection<BlockchainTxLogDocument>(collectionName)
    }

    /**
     * Create tx log object
     *
     * @param txLogId - unique id for the tx log
     * @param walletId - wallet id
     * @param action - action performed on the wallet
     * @param description - description of the action
     * @return BlockchainTxLog - tx log object
     */
    override fun createTxLogObject(txLogId: String, walletId: String, action: BlockchainTxAction, description: String?): BlockchainTxLog {
        val now = LocalDateTime.now()
        return BlockchainTxLogDocument(txLogId, walletId, action, description, now, now)
    }

    /**
     * Save tx log object
     *
     * @param txLog - tx log object
     * @return BlockchainTxLog - saved tx log object
     */
    override fun insert(txLog: BlockchainTxLog): BlockchainTxLog {
        val result = txLogCollection.insertOne(txLog as BlockchainTxLogDocument)

        if (result.wasAcknowledged()) {
            return txLog
        }
        throw Exception("Failed to insert blockchain transaction log")
    }

    /**
     * List tx logs for a wallet
     *
     * @return List<BlockchainTxLog> - list of tx logs
     */
    override fun list(): List<BlockchainTxLog> {
        return txLogCollection.find().toList()
    }

    /**
     * List pending
     *
     * @return List<BlockchainTxLog> - list of pending tx logs
     */
    override fun listPending(): List<BlockchainTxLog> {
        val pending = listOf(AtalaOperationStatus.AWAIT_CONFIRMATION, AtalaOperationStatus.PENDING_SUBMISSION)
        return txLogCollection.find(BlockchainTxLog::status `in` pending).toList()
    }

    /**
     * Update tx log object
     *
     * @param txLog
     * @return Boolean - true if update was successful
     */
    override fun update(txLog: BlockchainTxLog): Boolean {
        val result = txLogCollection.updateOne(txLog as BlockchainTxLogDocument, upsert())
        return result.wasAcknowledged()
    }

    /**
     * Find by id.
     *
     * @param txLogId
     * @return BlockchainTxLog - tx log object
     */
    override fun findById(txLogId: String): BlockchainTxLog {
        return txLogCollection.findOneById(txLogId) ?: throw NoSuchElementException("No tx log found with id $txLogId")
    }

    /**
     * Exists
     *
     * @param txLogId - tx log id
     * @return Boolean - true if tx log exists
     */
    override fun exists(txLogId: String): Boolean {
        return txLogCollection.findOneById(txLogId) != null
    }
}
