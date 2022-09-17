package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.didcomm.model.DidCommSecret
import com.rootsid.wal.library.didcomm.storage.DidCommSecretStorage
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.DidCommSecretDocument
import org.litote.kmongo.*

class SecretResolverDocStorage(db: MongoDatabase? = null, collectionName: String = "didcomm_secrets") : DidCommSecretStorage {
    private val collection: MongoCollection<DidCommSecretDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.collection = mongoConn.getCollection<DidCommSecretDocument>(collectionName)
    }

    override fun insert(kid: String, secretJson: Map<String, Any>): DidCommSecret {
        DidCommSecretDocument(kid, secretJson).let {
            val result = collection.insertOne(it)

            if (result.wasAcknowledged()) {
                return it
            }
        }

        throw RuntimeException("Failed inserting secret in storage.")
    }

    override fun findById(kid: String): DidCommSecret =
        collection.findOne(DidCommSecret::_id eq kid) ?: throw Exception("Secret '$kid' not found.")

    override fun findIdsIn(kids: List<String>): Set<String> =
        collection.find(DidCommSecret::_id `in` kids).projection(DidCommSecret::_id).map { it._id }.toSet()

    override fun list(): List<DidCommSecret> = collection.find().toList()

    override fun listIds(): List<String> = collection.find().projection(DidCommSecret::_id).map { it._id }.toList()
}
