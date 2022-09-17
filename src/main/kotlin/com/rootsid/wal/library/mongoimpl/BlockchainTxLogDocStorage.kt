package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.BlockchainTxLogDocument
import com.rootsid.wal.library.wallet.model.BlockchainTxAction
import com.rootsid.wal.library.wallet.model.BlockchainTxLog
import com.rootsid.wal.library.wallet.storage.BlockchainTxLogStorage
import org.litote.kmongo.*
import java.util.*

class BlockchainTxLogDocStorage(db: MongoDatabase? = null, collectionName: String = "txLog") : BlockchainTxLogStorage {
    private val txLogCollection: MongoCollection<BlockchainTxLogDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.txLogCollection = mongoConn.getCollection<BlockchainTxLogDocument>(collectionName)
    }

    override fun createTxLogObject(txLogId: String, walletId: String, action: BlockchainTxAction, description: String?): BlockchainTxLog {
        return BlockchainTxLogDocument(txLogId, walletId, action, description)
    }

    override fun insert(txLog: BlockchainTxLog): BlockchainTxLog {
        val result = txLogCollection.insertOne(txLog as BlockchainTxLogDocument)

        if (result.wasAcknowledged()) {
            return txLog
        }
        throw Exception("Failed to insert blockchain transaction log")
    }

    override fun list(): List<BlockchainTxLog> {
        return txLogCollection.find().toList()
    }

    /**
     * Update tx log with new entry     *
     * @param txLog updated tx log data object
     * @return true if the operation was acknowledged
     */
    override fun update(txLog: BlockchainTxLog): Boolean {
        val result = txLogCollection.updateOne(txLog as BlockchainTxLogDocument, upsert())
        return result.wasAcknowledged()
    }

    override fun findById(txLogId: String): BlockchainTxLog {
        return txLogCollection.findOneById(txLogId) ?: throw NoSuchElementException("No tx log found with id $txLogId")
    }

    override fun exists(txLogId: String): Boolean {
        return txLogCollection.findOneById(txLogId) != null
    }

}