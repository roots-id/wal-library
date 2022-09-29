package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.Claim
import com.rootsid.wal.library.dlt.model.VerifiedCredential
import kotlinx.serialization.Serializable

/**
 * Credential
 *
 * @property alias
 * @property issuingDidAlias
 * @property claim - Plain json claim
 * @property verifiedCredential - Signed VC and proof (This is the real VC)
 * @property batchId - Required for revocation
 * @property credentialHash - Required for revocation
 * @property operationHash - To retrieve the operation status from the node
 * @constructor Create empty Credential
 */
@Serializable
data class IssuedCredential(
    override val alias: String,
    var issuingDidAlias: String,
    val claim: Claim,
    override var verifiedCredential: VerifiedCredential,
    var batchId: String,
    var operationId: MutableList<String>,
    var credentialHash: String,
    var operationHash: String
) : Credential
