package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.wallet.model.ImportedCredential
import com.rootsid.wal.library.wallet.model.IssuedCredential
import com.rootsid.wal.library.wallet.model.Wallet

/**
 * Wallet document
 *
 * @property _id - wallet id
 * @property seed - wallet seed
 * @property dids - list of dids
 * @property importedCredentials - List of imported (Issued elsewhere)
 * @property issuedCredentials - List of credentials issued by a DID from this wallet
 * @constructor Create empty Wallet document
 */
data class WalletDocument(
    override val _id: String,
    override val seed: String,
    override var dids: MutableList<Did> = mutableListOf(),
    override var importedCredentials: MutableList<ImportedCredential> = mutableListOf(),
    override var issuedCredentials: MutableList<IssuedCredential> = mutableListOf()
) : Wallet
