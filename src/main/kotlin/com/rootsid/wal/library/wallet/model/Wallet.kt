package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.Did
import java.io.Serializable

/**
 * Wallet
 *
 * @property _id - wallet id
 * @property seed - seed to derive keys
 * @property dids - list of dids
 * @property importedCredentials - List of imported (Issued elsewhere)
 * @property issuedCredentials - List of credentials issued by a DID from this wallet
 */
interface Wallet: Serializable {
    val _id: String
    val seed: String
    var dids: MutableList<Did>
    var importedCredentials: MutableList<ImportedCredential>
    var issuedCredentials: MutableList<IssuedCredential>
}

/**
 * Add did
 *
 * @param did
 */
fun Wallet.addDid(did: Did) {
    dids.add(did)
}

/**
 * Add imported credential
 *
 * @param credential
 */
fun Wallet.addImportedCredential(credential: ImportedCredential) {
    importedCredentials.add(credential)
}

/**
 * Add issued credential
 *
 * @param credential
 */
fun Wallet.addIssuedCredential(credential: IssuedCredential) {
    issuedCredentials.add(credential)
}
