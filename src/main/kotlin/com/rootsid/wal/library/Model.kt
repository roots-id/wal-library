package com.rootsid.wal.library

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.identity.PrismDid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
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
@Serializable
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
 * @property operationHash
 * @property keyPairs
 * @constructor Create empty D i d
 */
@Serializable
data class DID(
    val alias: String,
    val didIdx: Int,
    val uriCanonical: String,
    val uriLongForm: String,
    var operationHash: String = "",
    var keyPairs: MutableList<KeyPair> = mutableListOf()
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
@Serializable
data class KeyPair(
    val keyId: String,
    val didIdx: Int,
    val keyType: Int,
    val keyIdx: Int,
    val privateKey: String,
    val publicKey: String
)

/**
 * Verified credential
 *
 * @property encodedSignedCredential
 * @property proof
 * @constructor Create empty Verified credential
 */
@Serializable
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
@Serializable
data class Credential(
    val _id: String,
    // Plain json claim
    val claim: Claim,
    // Signed VC and proof (This is the real VC)
    var verifiedCredential: VerifiedCredential,
    // Required for revocation
    var batchId: String,
    // Required for revocation
    var credentialHash: String,
    // Required for revocation
    var operationHash: String
)

data class UnpackResult(
    val message: String,
    val from: String?,
    val to: String,
    val res: org.didcommx.didcomm.model.UnpackResult
)
