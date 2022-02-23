package com.rootsid.wal.library

import com.mongodb.client.MongoDatabase
import org.litote.kmongo.*
import pbandk.FieldDescriptor

/**
 * Open db
 *
 * @return a MongoDatabase representing 'wal' database
 */
fun openDb(): MongoDatabase {
    // TODO: make this configurable so it can use other connection settings
    val client = KMongo.createClient() // get com.mongodb.MongoClient new instance
    // TODO: make databaseName configurable
    return client.getDatabase("wal") // normal java driver usage
}

/**
 * Insert wallet
 *
 * @param db MongoDB Client
 * @param wallet Wallet data object to add into the database
 * @return true if the operation was acknowledged
 */
fun insertWallet(db: MongoDatabase, wallet: Wallet): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val result = collection.insertOne(wallet)
    return result.wasAcknowledged()
}

/**
 * Insert credential
 *
 * @param db MongoDB Client
 * @param credential Credential data object to add into the database
 * @return true if the operation was acknowledged
 */
fun insertCredential(db: MongoDatabase, credential: Credential): Boolean {
    val collection = db.getCollection<Credential>("credential")
    val result = collection.insertOne(credential)
    return result.wasAcknowledged()
}

/**
 * Find wallet
 *
 * @param db MongoDB Client
 * @param walletName name of the wallet to find
 * @return true if the wallet was found
 */
fun findWallet(db: MongoDatabase, walletName: String): Wallet {
    val collection = db.getCollection<Wallet>("wallet")
    return collection.findOne(Wallet::_id eq walletName)
        ?: throw NoSuchElementException("Wallet '$walletName' not found.")
}

/**
 * Find credential
 *
 * @param db MongoDB Client
 * @param credentialAlias alias of the credential to find
 * @return true if the credential was found
 */
fun findCredential(db: MongoDatabase, credentialAlias: String): Credential {
    val collection = db.getCollection<Credential>("credential")
    return collection.findOne(Wallet::_id eq credentialAlias)
        ?: throw NoSuchElementException("Credential '$credentialAlias' not found.")
}

/**
 * List wallets
 *
 * @param db MongoDB Client
 * @return list of stored wallet names
 */
fun listWallets(db: MongoDatabase): List<Wallet> {
    val collection = db.getCollection<Wallet>("wallet")
    return collection.find().toList()
}

/**
 * Wallet exists
 *
 * @param db MongoDB Client
 * @param walletName name of the wallet to find
 * @return true if the wallet was found
 */
fun walletExists(db: MongoDatabase, walletName: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName'}")
    return wallet != null
}

/**
 * Did alias exists
 *
 * @param db MongoDB Client
 * @param walletName name of the wallet storing the did
 * @param didAlias alias of the did
 * @return true if the did was found
 */
fun didAliasExists(db: MongoDatabase, walletName: String, didAlias: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName','dids':{${MongoOperator.elemMatch}: {'alias':'$didAlias'}}}")
    return wallet != null
}

/**
 * Update wallet
 *
 * @param db MongoDB Client
 * @param wallet updated Wallet data object
 * @return true if the operation was acknowledged
 */
fun updateWallet(db: MongoDatabase, wallet: Wallet): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val result = collection.updateOne(wallet, upsert())
    return result.wasAcknowledged()
}
