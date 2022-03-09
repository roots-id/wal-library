package com.rootsid.wal.library

import com.mongodb.client.MongoDatabase
import org.litote.kmongo.*

/**
 * Open db
 *
 * @return a MongoDatabase representing 'wal' database
 */
fun openDb(): MongoDatabase {
    // TODO: make this configurable so it can use other connection settings
    val client = KMongo.createClient() // get com.mongodb.MongoClient new instance
    // TODO: make databaseName configurable
    return client.getDatabase(Config.DB_NAME) // normal java driver usage
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
 * Find wallet
 *
 * @param db MongoDB Client
 * @param walletName name of the wallet to find
 * @return wallet data object
 */
fun findWallet(db: MongoDatabase, walletName: String): Wallet {
    val collection = db.getCollection<Wallet>("wallet")
    return collection.findOne(Wallet::_id eq walletName)
        ?: throw Exception("Wallet '$walletName' not found.")
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
 * Key id exists
 *
 * @param db MongoDB Client
 * @param walletName name of the wallet storing the did
 * @param didAlias alias of the did
 * @param keyId key identifier
 * @return true if the keyId was found
 */
fun keyIdExists(db: MongoDatabase, walletName: String, didAlias: String, keyId: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName','dids':{${MongoOperator.elemMatch}: {'alias':'$didAlias'}}, 'dids.keyPairs.keyId':'$keyId'}")
    return wallet != null
}

/**
 * Issued credential alias exists
 *
 * @param db MongoDB Client
 * @param issuedCredentialAlias credential alias to find
 * @return true if the did was found
 */
fun issuedCredentialAliasExists(db: MongoDatabase, walletName: String, issuedCredentialAlias: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName','issuedCredentials':{${MongoOperator.elemMatch}: {'alias':'$issuedCredentialAlias'}}}")
    return wallet != null
}

/**
 * Credential alias exists
 *
 * @param db MongoDB Client
 * @param credentialAlias credential alias to find
 * @return true if the did was found
 */
fun credentialAliasExists(db: MongoDatabase, walletName: String, credentialAlias: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName','credentials':{${MongoOperator.elemMatch}: {'alias':'$credentialAlias'}}}")
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
