package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.Did
import java.io.Serializable

/**
 * Wallet
 *
 * @property _id
 * @property dids
 * @property importedCredentials
 * @property issuedCredentials
 * @constructor Create empty Wallet
 */
interface Wallet: Serializable {
    val _id: String // name of the wallet
    val seed: String
    var dids: MutableList<Did>

    // List of imported (Issued elsewhere)
    var importedCredentials: MutableList<ImportedCredential>

    // List of credentials issued by a DID from this wallet
    var issuedCredentials: MutableList<IssuedCredential>
}

fun Wallet.addDid(did: Did) {
    dids.add(did)
}

fun Wallet.addImportedCredential(credential: ImportedCredential) {
    importedCredentials.add(credential)
}

fun Wallet.addIssuedCredential(credential: IssuedCredential) {
    issuedCredentials.add(credential)
}
