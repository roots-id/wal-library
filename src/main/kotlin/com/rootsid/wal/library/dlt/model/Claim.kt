package com.rootsid.wal.library.dlt.model

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.identity.PrismDid
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Claim
 *
 * @property subjectDid - Subject DID is the DID of the entity who is the subject of the claim.
 * @property content - The claim content. This is a JSON string.
 * @constructor Create empty Claim
 */
@Serializable
data class Claim(
    val subjectDid: String,
    val content: String
)

/**
 * To credential claim
 *
 * Convert a Claim to PRISM CredentialClaim
 */
fun Claim.toCredentialClaim() = CredentialClaim(
    PrismDid.fromString(this.subjectDid),
    Json.decodeFromString(this.content)
)
