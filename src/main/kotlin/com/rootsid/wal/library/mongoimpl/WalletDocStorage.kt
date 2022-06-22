package com.rootsid.wal.library.mongoimpl

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.rootsid.wal.library.mongoimpl.config.DefaultMongoDbConn
import com.rootsid.wal.library.mongoimpl.document.WalletDocument
import com.rootsid.wal.library.wallet.model.Wallet
import com.rootsid.wal.library.wallet.storage.WalletStorage
import org.litote.kmongo.*


class WalletDocStorage(db: MongoDatabase? = null, collectionName: String = "wallet") : WalletStorage {
    private val walletCollection: MongoCollection<WalletDocument>

    init {
        val mongoConn = db ?: DefaultMongoDbConn.open()

        this.walletCollection = mongoConn.getCollection<WalletDocument>(collectionName)
    }

    /**
     * Insert wallet
     *
     * @param wallet Wallet data object to add into the database
     * @return true if the operation was acknowledged
     */
    override fun insert(wallet: Wallet): Wallet {
        val result = walletCollection.insertOne(wallet as WalletDocument)

        if (result.wasAcknowledged()) {
            return wallet
        }

        throw Exception("Failed to insert wallet")
    }

    /**
     * Update wallet
     *
     * @param wallet updated Wallet data object
     * @return true if the operation was acknowledged
     */
    override fun update(wallet: Wallet): Boolean {
        val result = walletCollection.updateOne(wallet as WalletDocument, upsert())
        return result.wasAcknowledged()
    }

    /**
     * Find wallet
     *
     * @param name name of the wallet to find
     * @return wallet data object
     */
    override fun findByName(name: String): Wallet {
        return walletCollection.findOne(Wallet::_id eq name) ?: throw Exception("Wallet '$name' not found.")
    }

    /**
     * List wallets
     *
     * @return list of stored wallet names
     */
    override fun list(): List<Wallet> {
        return walletCollection.find().toList()
    }

    /**
     * Wallet exists
     *
     * @param name name of the wallet to find
     * @return true if the wallet was found
     */
    override fun exists(name: String): Boolean {
        val wallet = walletCollection.findOne("{_id:'$name'}")
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
    fun didAliasExists(walletName: String, didAlias: String): Boolean {
        val wallet = walletCollection.findOne("{_id:'$walletName','dids':{${MongoOperator.elemMatch}: {'alias':'$didAlias'}}}")
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
    fun keyIdExists(walletName: String, didAlias: String, keyId: String): Boolean {
        val wallet =
            walletCollection.findOne("{_id:'$walletName','dids':{${MongoOperator.elemMatch}: {'alias':'$didAlias'}}, 'dids.keyPairs.keyId':'$keyId'}")
        return wallet != null
    }

    /**
     * Issued credential alias exists
     *
     * @param db MongoDB Client
     * @param issuedCredentialAlias credential alias to find
     * @return true if the did was found
     */
    fun issuedCredentialAliasExists(walletName: String, issuedCredentialAlias: String): Boolean {
        val wallet =
            walletCollection.findOne("{_id:'$walletName','issuedCredentials':{${MongoOperator.elemMatch}: {'alias':'$issuedCredentialAlias'}}}")
        return wallet != null
    }

    /**
     * Credential alias exists
     *
     * @param db MongoDB Client
     * @param credentialAlias credential alias to find
     * @return true if the did was found
     */
    fun credentialAliasExists(walletName: String, credentialAlias: String): Boolean {
        val wallet = walletCollection.findOne("{_id:'$walletName','credentials':{${MongoOperator.elemMatch}: {'alias':'$credentialAlias'}}}")
        return wallet != null
    }
}
