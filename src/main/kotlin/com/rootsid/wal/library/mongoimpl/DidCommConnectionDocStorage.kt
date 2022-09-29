package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.didcomm.model.DidCommConnection
import com.rootsid.wal.library.didcomm.storage.DidCommConnectionStorage
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.DidCommConnectionDocument
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection

class DidCommConnectionDocStorage(db: MongoDatabase? = null, collectionName: String = "didcomm_connections") :
    DidCommConnectionStorage {
    private val collection: MongoCollection<DidCommConnectionDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.collection = mongoConn.getCollection<DidCommConnectionDocument>(collectionName)
    }

    /**
     * Insert
     *
     * @param conn - connection to be inserted
     * @return DidCommConnection - inserted connection
     */
    override fun insert(conn: DidCommConnection): DidCommConnection {
        val result = collection.insertOne(conn as DidCommConnectionDocument)

        if (result.wasAcknowledged()) {
            return conn
        }

        throw RuntimeException("Failed inserting didcomm connection in storage.")
    }

    /**
     * Find by id
     *
     * @param id - id of connection to be found
     * @return DidCommConnection - found connection
     */
    override fun findById(id: String): DidCommConnection =
        collection.findOne(DidCommConnection::_id eq id) ?: throw Exception("DidComm connection '$id' not found.")

    override fun list(): List<DidCommConnection> = collection.find().toList()
}
