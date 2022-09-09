package com.rootsid.wal.library.wallet.storage

import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.wallet.model.Wallet
import java.util.*

interface WalletStorage {
    fun insert(wallet: Wallet): Wallet

    fun createWalletObject(walletId: String, seed: String) : Wallet

    fun update(wallet: Wallet): Boolean

    fun findById(walletId: String): Wallet

    fun exists(walletId: String): Boolean

    fun list(): List<Wallet>

    fun findDidByAlias(walletId: String, alias: String) : Optional<Did>

    fun listDids(walletId: String): List<Did>
}
