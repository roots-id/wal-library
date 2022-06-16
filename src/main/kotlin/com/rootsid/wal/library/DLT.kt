package com.rootsid.wal.library

import io.iohk.atala.prism.api.VerificationResult
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.credentials.json.JsonBasedCredential
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import io.iohk.atala.prism.protos.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Verify issued credential
 *
 * @param wallet Wallet containing the credential
 * @param credentialAlias Alias of Credential to verify
 * @return Verification result
 */
// TODO: refactor to a single verifyCredential function
fun verifyIssuedCredential(wallet: Wallet, credentialAlias: String): List<String> {
    val credentials = wallet.issuedCredentials.filter { it.alias == credentialAlias }
    if (credentials.isNotEmpty()) {
        val credential = credentials[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
        // Use encodeDefaults to generate empty siblings field on proof
        val format = Json { encodeDefaults = true }
        val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))

        return runBlocking {
            nodeAuthApi.verify(signed, proof).toMessageArray()
        }
    } else {
        throw Exception("Credential '$credentialAlias' not found.")
    }
}

private fun VerificationResult.toMessageArray(): List<String> {
    val messages = mutableListOf<String>()
    for (message in this.verificationErrors) {
        messages.add(message.errorMessage)
    }
    return messages
}

/**
 * Verify imported credential
 *
 * @param wallet Wallet containing the credential
 * @param credentialAlias Alias of credential to verify
 * @return Verification result
 */
// TODO: refactor to a single verifyCredential function
fun verifyImportedCredential(wallet: Wallet, credentialAlias: String): List<String> {
    val credentials = wallet.importedCredentials.filter { it.alias == credentialAlias }
    if (credentials.isNotEmpty()) {
        val credential = credentials[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
        // Use encodeDefaults to generate empty siblings field on proof
        val format = Json { encodeDefaults = true }
        val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))

        return runBlocking {
            nodeAuthApi.verify(signed, proof).toMessageArray()
        }
    } else {
        throw Exception("Credential '$credentialAlias' not found.")
    }
}

/**
 * Grpc config
 * Done this way to allow programmatic override of the grpc config
 * @constructor Create empty Grpc config
 */
class GrpcConfig {
    companion object {
        var host: String = System.getenv("PRISM_NODE_HOST") ?: ""
        var port: String = System.getenv("PRISM_NODE_PORT") ?: "50053"
        fun options() = GrpcOptions("https", host, port.toInt())
    }
}
