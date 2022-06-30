package com.rootsid.wal.library.wallet.storage

import com.rootsid.wal.library.wallet.model.Wallet

interface WalletStorage {
    fun insert(wallet: Wallet): Wallet

    fun update(wallet: Wallet): Boolean

    fun findByName(name: String): Wallet

    fun exists(name: String): Boolean

    fun list(): List<Wallet>
}
