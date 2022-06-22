package com.rootsid.wal.library.wallet.storage.document

import com.mongodb.ConnectionString
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.wallet.model.Wallet
import com.rootsid.wal.library.wallet.storage.Storage
import io.iohk.atala.prism.crypto.util.BytesOps
import org.litote.kmongo.*

class DocumentStorage(connectionString: ConnectionString, databaseName: String) : Storage {
    private val db: MongoDatabase = KMongo.createClient(connectionString).getDatabase(databaseName)

    /**
     * Insert wallet
     *
     * @param db MongoDB Client
     * @param wallet Wallet data object to add into the database
     * @return true if the operation was acknowledged
     */
    override fun createWallet(name: String, seed: ByteArray): Wallet {
        val wallet = WalletDocument(name, BytesOps.bytesToHex(seed))
        val collection = db.getCollection<Wallet>("wallet")
        val result = collection.insertOne(wallet)
        if (result.wasAcknowledged()) {
            return wallet
        } else {
            throw Exception("Failed to insert wallet")
        }
    }
}

// TODO: Refactor functions below to use the same pattern as in the other storage implementations

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
