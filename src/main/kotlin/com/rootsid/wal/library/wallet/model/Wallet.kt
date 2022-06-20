package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.Did

/**
 * Wallet
 *
 * @property _id
 * @property mnemonic
 * @property passphrase
 * @property dids
 * @property importedCredentials
 * @property issuedCredentials
 * @constructor Create empty Wallet
 */
interface Wallet {
    val _id: String // name of the wallet
    val seed: String
    var dids: MutableList<Did>
    // List of imported (Issued elsewhere)
    var importedCredentials: MutableList<ImportedCredential>
    // List of credentials issued by a DID from this wallet
    var issuedCredentials: MutableList<IssuedCredential>

    fun addDid(did: Did) {
        dids.add(did)
    }

    fun addImportedCredential(credential: ImportedCredential) {
        importedCredentials.add(credential)
    }

    fun addIssuedCredential(credential: IssuedCredential) {
        issuedCredentials.add(credential)
    }
}
