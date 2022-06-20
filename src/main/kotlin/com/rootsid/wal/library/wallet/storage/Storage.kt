package com.rootsid.wal.library.wallet.storage

import com.rootsid.wal.library.wallet.model.Wallet

interface Storage {
    fun createWallet(name: String, seed: ByteArray): Wallet
}
