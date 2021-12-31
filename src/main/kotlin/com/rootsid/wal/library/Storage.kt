package com.rootsid.wal.library

import com.mongodb.client.MongoDatabase
import org.litote.kmongo.*

/**
 * Open db
 * TODO: Add Parameters for Client configuration.
 * @return
 */
fun openDb(): MongoDatabase {
    val client = KMongo.createClient() // get com.mongodb.MongoClient new instance
    return client.getDatabase("wal") // normal java driver usage
}

/**
 * Insert wallet
 *
 * @param db
 * @param wallet
 * @return
 */
fun insertWallet(db: MongoDatabase, wallet: Wallet): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val result = collection.insertOne(wallet)
    return result.wasAcknowledged()
}

/**
 * Insert credential
 *
 * @param db
 * @param credential
 * @return
 */
fun insertCredential(db: MongoDatabase, credential: Credential): Boolean {
    val collection = db.getCollection<Credential>("credential")
    val result = collection.insertOne(credential)
    return result.wasAcknowledged()
}

/**
 * Find wallet
 *
 * @param db
 * @param walletName
 * @return
 */
fun findWallet(db: MongoDatabase, walletName: String): Wallet {
    val collection = db.getCollection<Wallet>("wallet")
    return collection.findOne(Wallet::_id eq walletName)
        ?: throw NoSuchElementException("Wallet '$walletName' not found.")
}

/**
 * Find credential
 *
 * @param db
 * @param credentialAlias
 * @return
 */
fun findCredential(db: MongoDatabase, credentialAlias: String): Credential {
    val collection = db.getCollection<Credential>("credential")
    return collection.findOne(Wallet::_id eq credentialAlias)
        ?: throw NoSuchElementException("Credential '$credentialAlias' not found.")
}

/**
 * Find wallets
 *
 * @param db
 * @return
 */
fun findWallets(db: MongoDatabase): List<Wallet> {
    val collection = db.getCollection<Wallet>("wallet")
    return collection.find().toList()
}

/**
 * Did alias exists
 *
 * @param db
 * @param walletName
 * @param didAlias
 * @return
 */
fun didAliasExists(db: MongoDatabase, walletName: String, didAlias: String): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val wallet = collection.findOne("{_id:'$walletName','dids':{${MongoOperator.elemMatch}: {'alias':'$didAlias'}}}")
    return wallet != null
}

/**
 * Update wallet
 *
 * @param db
 * @param wallet
 * @return
 */
fun updateWallet(db: MongoDatabase, wallet: Wallet): Boolean {
    val collection = db.getCollection<Wallet>("wallet")
    val result = collection.updateOne(wallet, upsert())
    return result.wasAcknowledged()
}
