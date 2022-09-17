package com.rootsid.wal.library.mongoimpl.config

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.Constant
import org.bson.UuidRepresentation
import org.litote.kmongo.KMongo

object DefaultMongoDbConn : MongoDbConn {
    private var db: MongoDatabase = openDb()

    fun open(): MongoDatabase {
        return db
    }

    override fun getDbName(): String {
        return Constant.DB_NAME
    }

    override fun getMongoClient(): MongoClient {
        return KMongo.createClient(
            MongoClientSettings
                .builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build()
        )
    }
}
