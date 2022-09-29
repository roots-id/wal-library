package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.VerifiedCredential
import kotlinx.serialization.Serializable

/**
 * Imported credential
 *
 * @property alias
 * @property verifiedCredential - Signed VC and proof (This is the real VC)
 * @constructor Create empty Imported credential
 */
@Serializable
data class ImportedCredential(
    override val alias: String,
    override var verifiedCredential: VerifiedCredential
) : Credential
