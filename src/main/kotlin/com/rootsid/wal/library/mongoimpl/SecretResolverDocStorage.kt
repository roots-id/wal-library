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

    /**
     * Insert a new secret into the database
     *
     * @param kid - the key id
     * @param secretJson - the secret as a json string
     * @return DidCommSecret - the secret
     */
    override fun insert(kid: String, secretJson: Map<String, Any>): DidCommSecret {
        DidCommSecretDocument(kid, secretJson).let {
            val result = collection.insertOne(it)

            if (result.wasAcknowledged()) {
                return it
            }
        }

        throw RuntimeException("Failed inserting secret in storage.")
    }

    /**
     * Find by id
     *
     * @param kid - the key id
     * @return DidCommSecret - the secret
     */
    override fun findById(kid: String): DidCommSecret =
        collection.findOne(DidCommSecret::_id eq kid) ?: throw Exception("Secret '$kid' not found.")

    /**
     * Find ids in
     *
     * @param kids - the key ids
     * @return List<DidCommSecret> - the secrets
     */
    override fun findIdsIn(kids: List<String>): Set<String> =
        collection.find(DidCommSecret::_id `in` kids).projection(DidCommSecret::_id).map { it._id }.toSet()

    /**
     * List all secrets
     *
     * @return List<DidCommSecret> - the secrets
     */
    override fun list(): List<DidCommSecret> = collection.find().toList()

    /**
     * List ids
     *
     * @return List<String> - the secret ids
     */
    override fun listIds(): List<String> = collection.find().projection(DidCommSecret::_id).map { it._id }.toList()
}
