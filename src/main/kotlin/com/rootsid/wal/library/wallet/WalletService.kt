package com.rootsid.wal.library.wallet

import com.rootsid.wal.library.Constant
import com.rootsid.wal.library.mongoimpl.document.WalletDocument
import com.rootsid.wal.library.wallet.model.Wallet
import com.rootsid.wal.library.wallet.storage.WalletStorage
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.crypto.derivation.MnemonicCode
import io.iohk.atala.prism.crypto.util.BytesOps

class WalletService(private val walletStorage: WalletStorage) {

    /**
     * New wallet
     *
     * @param name Wallet name
     * @param mnemonic mnemonic, leave empty to generate a random one
     * @param passphrase passphrase
     * @return a new wallet
     */
    fun createWallet(name: String, mnemonic: String, passphrase: String): Wallet {
        val seed = generateSeed(mnemonic, passphrase)
        return walletStorage.insert(WalletDocument(name, BytesOps.bytesToHex(seed)))
    }

    /**
     * Generate seed
     *
     * @param mnemonic Mnemonic phrase, separated by Config.MNEMONIC_SEPARATOR. If empty, a random one will be generated
     * @param passphrase Passphrase to protect the seed with a password
     * @return seed
     */
    private fun generateSeed(mnemonic: String, passphrase: String): ByteArray {
        val seed: ByteArray
        val mnemonicList: MnemonicCode
        if (mnemonic.trim().isBlank()) {
            mnemonicList = MnemonicCode(KeyDerivation.randomMnemonicCode().words)
            seed = KeyDerivation.binarySeed(mnemonicList, passphrase)
        } else {
            try {
                mnemonicList = MnemonicCode(mnemonic.split(Constant.MNEMONIC_SEPARATOR).map { it.trim() })
                seed = KeyDerivation.binarySeed(mnemonicList, passphrase)
            } catch (e: Exception) {
                throw Exception("Invalid mnemonic phrase")
            }
        }
        return seed
    }

    // newDid(walletId: String, didAlias: String, issuer: Boolean): Did {
    // - newDid(didAlias: String, didIdx: Int, seed: ByteArray, issuer: Boolean): Did
    // - add did to wallet

    // getDidDocument(walletId: String, didAlias: String)
    // --> getDidDocument(did: String): DidDocument

    // publishDid(walletId: String, didAlias: String)
    // - publishDid(did: Did, seed: ByteArray) (operationId, operationHash) -> DltDidUpdate
    // - flag did as published

    // addKey(walletId: String, didAlias: String, keyId: String, keyTypeValue: Int)
    // - addKey(did: Did, seed: ByteArray, keyId: String, keyTypeValue: Int) -> DltDidUpdate
    // - update did in wallet
}
