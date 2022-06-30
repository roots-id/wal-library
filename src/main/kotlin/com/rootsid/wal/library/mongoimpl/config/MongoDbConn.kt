package com.rootsid.wal.library.mongoimpl.config

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase

interface MongoDbConn {
    fun getDbName(): String

    fun getMongoClient() : MongoClient

    /**
     * Open db
     *
     * @return a MongoDatabase representing 'wal' database
     */
    fun openDb(): MongoDatabase {
        // TODO: make this configurable so it can use other connection settings
        val client = getMongoClient() // get com.mongodb.MongoClient new instance
        // TODO: make databaseName configurable
        return client.getDatabase(getDbName()) // normal java driver usage
    }
}
