package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.didcom.model.DidComSecret
import com.rootsid.wal.library.didcom.storage.DidComSecretStorage
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.DidComSecretDocument
import org.litote.kmongo.*

class SecretResolverDocStorage(db: MongoDatabase? = null, collectionName: String = "didComSecrets") : DidComSecretStorage {
    private val collection: MongoCollection<DidComSecretDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.collection = mongoConn.getCollection<DidComSecretDocument>(collectionName)
    }

    override fun insert(kid: String, secretJson: Map<String, Any>): DidComSecret {
        DidComSecretDocument(kid, secretJson).let {
            val result = collection.insertOne(it)

            if (result.wasAcknowledged()) {
                return it
            }
        }

        throw RuntimeException("Failed inserting secret in storage.")
    }

    override fun findById(kid: String): DidComSecret =
        collection.findOne(DidComSecret::_id eq kid) ?: throw Exception("Secret '$kid' not found.")

    override fun findIdsIn(kids: List<String>): Set<String> =
        collection.find(DidComSecret::_id `in` kids).projection(DidComSecret::_id).map { it._id }.toSet()

    override fun list(): List<DidComSecret> = collection.find().toList()

    override fun listIds(): List<String> = collection.find().projection(DidComSecret::_id).map { it._id }.toList()
}
