package com.rootsid.wal.library.wallet.model

import kotlinx.serialization.Serializable

/**
 * Credential
 *
 * @property alias
 * @property issuingDidAlias
 * @property claim
 * @property verifiedCredential
 * @property batchId
 * @property credentialHash
 * @property operationHash
 * @property revoked
 * @constructor Create empty Credential
 */
@Serializable
data class IssuedCredential(
    val alias: String,
    var issuingDidAlias: String,
    // Plain json claim
    val claim: Claim,
    // Signed VC and proof (This is the real VC)
    var verifiedCredential: VerifiedCredential,
    // Required for revocation
    var batchId: String,
    // Required for revocation
    var credentialHash: String,
    // Required for revocation
    var operationHash: String,
    var revoked: Boolean
)
