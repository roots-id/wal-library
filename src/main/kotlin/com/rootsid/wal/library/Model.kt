package com.rootsid.wal.library

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.identity.PrismDid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Wallet
 *
 * @property _id
 * @property mnemonic
 * @property passphrase
 * @property dids
 * @constructor Create empty Wallet
 */
data class Wallet(
    val _id: String, // name
    val mnemonic: List<String>,
    val passphrase: String,
    var dids: MutableList<DID> = mutableListOf()
)

/**
 * D i d
 *
 * @property alias
 * @property didIdx
 * @property uriCanonical
 * @property uriLongForm
 * @property hash
 * @property keyPaths
 * @constructor Create empty D i d
 */
data class DID(
    val alias: String,
    val didIdx: Int,
    val uriCanonical: String,
    val uriLongForm: String,
    var hash: String = "",
    var keyPaths: MutableList<KeyPath> = mutableListOf()
)

/**
 * Key path
 *
 * @property keyId
 * @property didIdx
 * @property keyType
 * @property keyIdx
 * @constructor Create empty Key path
 */
data class KeyPath(
    val keyId: String,
    val didIdx: Int,
    val keyType: Int,
    val keyIdx: Int
)

/**
 * Verified credential
 *
 * @property encodedSignedCredential
 * @property proof
 * @constructor Create empty Verified credential
 */
data class VerifiedCredential(
    val encodedSignedCredential: String,
    val proof: String
)

/**
 * Claim
 *
 * @property subjectDid
 * @property content
 * @constructor Create empty Claim
 */
data class Claim(
    val subjectDid: String,
    val content: String
)

/**
 * To credential claim
 *
 * Convert a Claim to PRISM CredentialClaim
 */
@OptIn(ExperimentalSerializationApi::class)
fun Claim.toCredentialClaim() = CredentialClaim(
    PrismDid.fromString(this.subjectDid),
    Json.decodeFromString(this.content)
)

/**
 * Credential
 *
 * @property _id
 * @property claim
 * @property verifiedCredential
 * @constructor Create empty Credential
 */
data class Credential(
    val _id: String,
    // Plain json claim
    val claim: Claim,
    // Signed VC and proof
    var verifiedCredential: VerifiedCredential
)
