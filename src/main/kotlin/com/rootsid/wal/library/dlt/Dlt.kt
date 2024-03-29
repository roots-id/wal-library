package com.rootsid.wal.library.dlt

import com.rootsid.wal.library.*
import com.rootsid.wal.library.dlt.model.*
import com.rootsid.wal.library.dlt.model.Did
import com.rootsid.wal.library.wallet.model.*
import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.VerificationResult
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationInfo
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.api.node.PrismDidState
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.credentials.json.JsonBasedCredential
import io.iohk.atala.prism.crypto.EC
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.crypto.keys.ECKeyPair
import io.iohk.atala.prism.crypto.keys.ECPrivateKey
import io.iohk.atala.prism.identity.*
import io.iohk.atala.prism.protos.*
import io.ipfs.multibase.Base58
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import pbandk.ByteArr
import pbandk.json.encodeToJsonString

class Dlt {
    private val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())

    /**
     * Use this function to get the blockchain transaction associated with an operationID
     * @param operationId operation identifier
     * @return blockchain Tx identifier
     */
    @OptIn(PrismSdkInternal::class)
    private fun transactionId(operationId: AtalaOperationId): String {
        val node = NodeServiceCoroutine.Client(GrpcClient(GrpcConfig.options()))
        val response = runBlocking {
            node.GetOperationInfo(GetOperationInfoRequest(ByteArr(operationId.value())))
        }
        return response.transactionId
    }

    /**
     * Get operation info. Returns the OperationInfo associated with an operationID
     *
     * @param operationId - operation identifier
     * @return
     */
    fun getOperationInfo(operationId: String): AtalaOperationInfo {
        return runBlocking { nodeAuthApi.getOperationInfo(AtalaOperationId.fromHex(operationId)) }
    }

    /**
     * Waits until the operationId status changes from [AtalaOperationStatus.PENDING_SUBMISSION] to [AtalaOperationStatus.AWAIT_CONFIRMATION]
     *
     * @param nodePublicApi PRISM node request handler
     * @param operationId operation Identifier
     * @return blockchain tx identifier
     */
    private fun waitForSubmission(nodePublicApi: NodePublicApi, operationId: AtalaOperationId): String {
        var status = runBlocking {
            nodePublicApi.getOperationInfo(operationId).status
        }
        while (status == AtalaOperationStatus.PENDING_SUBMISSION) {
            println("Current operation status: ${AtalaOperationStatus.asString(status)}\n")
            Thread.sleep(10000)
            status = runBlocking {
                nodePublicApi.getOperationInfo(operationId).status
            }
        }
        val txId = transactionId(operationId)
        println("Track the transaction in:\n- ${Constant.TESTNET_URL}$txId\n")
        return txId
    }

    /**
     * Waits until the operationId status changes to [AtalaOperationStatus.CONFIRMED_AND_REJECTED] or [AtalaOperationStatus.CONFIRMED_AND_APPLIED]
     *
     * @param nodePublicApi PRISM node request handler
     * @param operationId operation Identifier
     */
    private fun waitUntilConfirmed(nodePublicApi: NodePublicApi, operationId: AtalaOperationId) {
        var status = runBlocking {
            nodePublicApi.getOperationInfo(operationId).status
        }
        while (status != AtalaOperationStatus.CONFIRMED_AND_APPLIED &&
            status != AtalaOperationStatus.CONFIRMED_AND_REJECTED
        ) {
            println("Current operation status: ${AtalaOperationStatus.asString(status)}\n")
            Thread.sleep(10000)
            status = runBlocking {
                nodePublicApi.getOperationInfo(operationId).status
            }
        }
    }

    /**
     * Derive a key pair using a seed and a [KeyPath]
     *
     * @param keyPaths List containing key path information
     * @param seed seed
     * @param keyId Id of the key to derive
     * @return Key pair
     */
    @OptIn(PrismSdkInternal::class)
    private fun deriveKeyPair(keyPaths: MutableList<KeyPath>, seed: ByteArray, keyId: String): ECKeyPair {
        val keyPathList = keyPaths.filter { it.keyId == keyId }
        if (keyPathList.isNotEmpty()) {
            val keyPath = keyPathList[0]
            return KeyGenerator.deriveKeyFromFullPath(
                seed,
                keyPath.didIdx,
                PublicKeyUsage.fromProto(KeyUsage.fromValue(keyPath.keyTypeValue)),
                keyPath.keyIdx
            )
        } else {
            throw NoSuchElementException("Key ID '$keyId' not found.")
        }
    }

    /**
     * Private key map. This map is used to store the private keys of the keys that are used to sign the operations
     *
     * @param keyPaths - List containing key path information
     * @param seed - seed
     * @return Map containing the private keys
     */
    @OptIn(PrismSdkInternal::class)
    private fun privateKeyMap(keyPaths: MutableList<KeyPath>, seed: ByteArray): Map<String, ECPrivateKey> {
        val keyMap = mutableMapOf<String, ECPrivateKey>()
        keyPaths.forEach {
            keyMap[it.keyId] = KeyGenerator.deriveKeyFromFullPath(
                seed,
                it.didIdx,
                PublicKeyUsage.fromProto(KeyUsage.fromValue(it.keyTypeValue)),
                it.keyIdx
            ).privateKey
        }
        return keyMap
    }

    /**
     * New did
     *
     * @param didAlias Alias for the new DID
     * @param didIdx did index for key generation paths
     * @param seed key generation seed
     * @param issuer If true issuing and holder keys are included, otherwise only a master key pair is added
     * @return did (data class)
     */
    @OptIn(PrismSdkInternal::class)
    fun newDid(didAlias: String, didIdx: Int, seed: ByteArray, issuer: Boolean): Did {
        val keyPaths = mutableListOf<KeyPath>()
        val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed,
            didIdx,
            MasterKeyUsage,
            0
        )
        val masterKeyPathData = KeyPath(
            PrismDid.DEFAULT_MASTER_KEY_ID,
            KeyUsage.MASTER_KEY.value,
            didIdx,
            MasterKeyUsage.derivationIndex(),
            0
        )
        keyPaths.add(masterKeyPathData)

        val unpublishedDid = if (issuer) {
            val issuingKeyPair = KeyGenerator.deriveKeyFromFullPath(
                seed,
                didIdx,
                IssuingKeyUsage,
                0
            )
            val issuingKeyPathData = KeyPath(
                PrismDid.DEFAULT_ISSUING_KEY_ID,
                KeyUsage.ISSUING_KEY.value,
                didIdx,
                IssuingKeyUsage.derivationIndex(),
                0
            )
            keyPaths.add(issuingKeyPathData)

            val revocationKeyPair = KeyGenerator.deriveKeyFromFullPath(
                seed,
                didIdx,
                RevocationKeyUsage,
                0
            )
            val revocationKeyPathData = KeyPath(
                PrismDid.DEFAULT_REVOCATION_KEY_ID,
                KeyUsage.REVOCATION_KEY.value,
                didIdx,
                RevocationKeyUsage.derivationIndex(),
                0
            )
            keyPaths.add(revocationKeyPathData)

            PrismDid.buildExperimentalLongFormFromKeys(
                masterKeyPair.publicKey,
                issuingKeyPair.publicKey,
                revocationKeyPair.publicKey
            )
        } else {
            PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)
        }
        return Did(didAlias, didIdx, unpublishedDid.asCanonical().did.toString(), unpublishedDid.did.toString(), keyPaths)
    }

    /**
     * Get did state from prism node.
     *
     * @param did did:prism string
     * @return DID state
     */
    fun getDidState(did: String): PrismDidState {
        val prismDid = try {
            PrismDid.fromString(did)
        } catch (e: Exception) {
            throw Exception("not a Prism DID: $did")
        }
        return runBlocking { nodeAuthApi.getDidDocument(prismDid) }
    }

    /**
     * Get did document
     *
     * @param did did:prism URI
     * @return DID document
     */
    @OptIn(PrismSdkInternal::class)
    fun getDidDocument(did: String): DIDData {
        return getDidState(did).didData.toProto()
    }

    /**
     * Get did document as json
     *
     * @param did did:prism string
     * @return DID document in JsonString
     */
    @OptIn(PrismSdkInternal::class)
    fun getDidDocumentJson(did: String): String {
        return getDidDocument(did).encodeToJsonString()
    }

    /**
     * Get W3C compliant did document
     *
     * @param did a prism did
     * @return W3C compliant DID document in JsonObject
     */
    @OptIn(PrismSdkInternal::class)
    fun getDidDocumentW3C(did: String): JsonObject {
        fun byteArrayOfInts(ints: List<String>) = ByteArray(ints.size) { pos -> ints[pos].toInt().toByte() }

        fun String.decodeHex(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }
            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val prismDid = try {
            PrismDid.fromString(did)
        } catch (e: Exception) {
            throw Exception("not a Prism DID: $did")
        }
        val prismDoc = runBlocking { nodeAuthApi.getDidDocument(prismDid) }

        var didDocW3C = mutableMapOf(
            "@context" to JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/did/v1"))),
            "id" to JsonPrimitive(did),
            "assertionMethod" to JsonArray(listOf(JsonPrimitive(did + "#master0")))
        )
        var verificationMethods: MutableList<JsonObject> = ArrayList()
        // TODO parsing a string is not the best way to access the object. Need to figure out
        // how to access OneOf.CompressedEcKeyData directly
        for (pubk in prismDoc.didData.publicKeys) {
            val keyId = pubk.didPublicKey.toProto().id
            val dataStr = pubk.didPublicKey.toProto().keyData.toString()
                .replace("OneOf.CompressedEcKeyData(CompressedECKeyData(curve=secp256k1, data=[", "")
                .replace("], unknownFields={}))", "")
                .replace(" ", "")
            val dataArr = dataStr.split(",")
            val dataCompress = byteArrayOfInts(dataArr)
            val dataHexa = EC.toPublicKeyFromCompressed(dataCompress).getHexEncoded()
            verificationMethods.add(
                JsonObject(
                    mapOf(
                        "@context" to JsonArray(listOf(JsonPrimitive("https://w3id.org/security/v1"))),
                        "id" to JsonPrimitive(did + "#" + keyId),
                        "type" to JsonPrimitive("EcdsaSecp256k1VerificationKey2019"),
                        "controller" to JsonPrimitive(did),
                        "publicKeyBase58" to JsonPrimitive(Base58.encode(dataHexa.drop(2).decodeHex()))
                    )
                )
            )
        }
        didDocW3C["verificationMethod"] = JsonArray(verificationMethods)

        return JsonObject(didDocW3C)
    }

    /**
     * Publish did.
     * This will trigger a transaction to be sent to the blockchain.
     *
     * @param did - did to be published
     * @param seed - to generate the keys
     * @return Did
     */
    fun publishDid(did: Did, seed: ByteArray): Did {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val prismDid = PrismDid.fromString(did.uriLongForm)

        val nodePayloadGenerator = NodePayloadGenerator(
            prismDid as LongFormPrismDid,
            privateKeyMap(did.keyPaths, seed)
        )
        val createDidInfo = nodePayloadGenerator.createDid()
        val createDidOperationId = runBlocking {
            nodeAuthApi.createDid(
                createDidInfo.payload,
                prismDid,
                PrismDid.DEFAULT_MASTER_KEY_ID
            )
        }
        did.operationHash.add(createDidInfo.operationHash.hexValue)
        did.operationId.add(createDidOperationId.hexValue())

        return did
    }

    /**
     * Add key
     *
     * @param did did to add key to
     * @param seed seed to generate the keys
     * @param keyId key id
     * @param keyTypeValue key type
     * @return Did
     */
    @OptIn(PrismSdkInternal::class)
    fun addKey(did: Did, seed: ByteArray, keyId: String, keyTypeValue: Int): Did {
        val keyIdx = did.keyPaths.filter { it.keyTypeValue == keyTypeValue }.size
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())

        // TODO: masterKey index 0 may be revoked, do something to indicate the currently valid masterKey
        val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, did.didIdx, MasterKeyUsage, 0)
        val newKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed,
            did.didIdx,
            PublicKeyUsage.fromProto(KeyUsage.fromValue(keyTypeValue)),
            keyIdx
        )
        val keyUsage = KeyUsage.fromValue(keyTypeValue)
        val newKeyPairData = KeyPath(
            keyId,
            keyTypeValue,
            did.didIdx,
            PublicKeyUsage.fromProto(keyUsage).derivationIndex(),
            keyIdx
        )
        val nodePayloadGenerator = NodePayloadGenerator(
            PrismDid.fromString(did.uriLongForm) as LongFormPrismDid,
            mapOf(PrismDid.DEFAULT_MASTER_KEY_ID to masterKeyPair.privateKey)
        )
        val newKeyInfo = PrismKeyInformation(
            DidPublicKey(keyId, PublicKeyUsage.fromProto(keyUsage), newKeyPair.publicKey)
        )
        val updateDidInfo = nodePayloadGenerator.updateDid(
            previousHash = Sha256Digest.fromHex(did.operationHash.last()),
            masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
            keysToAdd = arrayOf(newKeyInfo)
        )
        val updateDidOperationId = runBlocking {
            nodeAuthApi.updateDid(
                payload = updateDidInfo.payload,
                did = PrismDid.fromString(did.uriCanonical).asCanonical(),
                masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                previousOperationHash = Sha256Digest.fromHex(did.operationHash.last()),
                keysToAdd = arrayOf(newKeyInfo),
                keysToRevoke = arrayOf()
            )
        }
        did.operationHash.add(updateDidInfo.operationHash.hexValue)
        did.keyPaths.add(newKeyPairData)
        did.operationId.add(updateDidOperationId.hexValue())
        return did
    }

    /**
     * Revoke key
     *
     * @param did did containing the key to be revoked
     * @param seed seed to generate the keys
     * @param keyId key identifier to be revoked
     * @return Did
     */
    fun revokeKey(did: Did, seed: ByteArray, keyId: String): Did {
        val keyPairList = did.keyPaths.filter { it.keyId == keyId }
        if (keyPairList.isNotEmpty()) {
            val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())

            // Key pairs to get private keys
            // TODO: masterKey index 0 may be revoked, do something to indicate the currently valid masterKey
            val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, did.didIdx, MasterKeyUsage, 0)

            val nodePayloadGenerator = NodePayloadGenerator(
                PrismDid.fromString(did.uriLongForm) as LongFormPrismDid,
                mapOf(PrismDid.DEFAULT_MASTER_KEY_ID to masterKeyPair.privateKey)
            )
            val updateDidInfo = nodePayloadGenerator.updateDid(
                previousHash = Sha256Digest.fromHex(did.operationHash.last()),
                masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                keysToRevoke = arrayOf(keyId)
            )
            val updateDidOperationId = runBlocking {
                nodeAuthApi.updateDid(
                    payload = updateDidInfo.payload,
                    did = PrismDid.fromString(did.uriCanonical).asCanonical(),
                    masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                    previousOperationHash = Sha256Digest.fromHex(did.operationHash.last()),
                    keysToAdd = arrayOf(),
                    keysToRevoke = arrayOf(keyId)
                )
            }
            // Key revocation flag
            keyPairList[0].revoked = true
            // Update DID last operation hash
            did.operationHash.add(updateDidInfo.operationHash.hexValue)
            did.operationId.add(updateDidOperationId.hexValue())
            return did
        } else {
            throw NoSuchElementException("Key identifier '$keyId' not found.")
        }
    }

    /**
     * Issue credential
     *
     * @param issuerDid issuer did
     * @param seed seed to generate the keys
     * @param issuedCredential credential to be issued
     * @return Pair - updated did and updated issued credential
     */
    fun issueCredential(issuerDid: Did, seed: ByteArray, issuedCredential: IssuedCredential): Pair<Did, IssuedCredential> {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val claims = mutableListOf<CredentialClaim>()
        // Key pairs to get private keys
        val issuingKeyPair = deriveKeyPair(issuerDid.keyPaths, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)

        claims.add(issuedCredential.claim.toCredentialClaim())

        val nodePayloadGenerator = NodePayloadGenerator(
            PrismDid.fromString(issuerDid.uriLongForm) as LongFormPrismDid,
            mapOf(PrismDid.DEFAULT_ISSUING_KEY_ID to issuingKeyPair.privateKey)
        )
        val credentialsInfo = nodePayloadGenerator.issueCredentials(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            claims.toTypedArray()
        )
        // credential batchId and hash are required for revocation
        issuedCredential.batchId = credentialsInfo.batchId.id
        val info = credentialsInfo.credentialsAndProofs[0]
        issuedCredential.credentialHash = info.signedCredential.hash().hexValue
        issuedCredential.operationHash = credentialsInfo.operationHash.hexValue
        issuedCredential.issuingDidAlias = issuerDid.alias
        issuedCredential.verifiedCredential = VerifiedCredential(
            info.signedCredential.canonicalForm,
            Json.decodeFromString<Proof>(info.inclusionProof.encode())
        )
        val issueCredentialsOperationId = runBlocking {
            nodeAuthApi.issueCredentials(
                credentialsInfo.payload,
                PrismDid.fromString(issuerDid.uriCanonical).asCanonical(),
                PrismDid.DEFAULT_ISSUING_KEY_ID,
                credentialsInfo.merkleRoot
            )
        }
        // Update DID last operation hash
        issuerDid.operationHash.add(credentialsInfo.operationHash.hexValue)
        issuerDid.operationId.add(issueCredentialsOperationId.hexValue())
        issuedCredential.operationId.add(issueCredentialsOperationId.hexValue())
        return Pair(issuerDid, issuedCredential)
    }

    /**
     * Revoke credential
     *
     * @param credential credential to be revoked
     * @param issuerDid issuer did
     * @param seed seed to generate the keys
     * @return Pair<Did, IssuedCredential>
     */
    fun revokeCredential(credential: IssuedCredential, issuerDid: Did, seed: ByteArray): Pair<Did, IssuedCredential> {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        // Key pairs to get private keys
        val revocationKeyPair = deriveKeyPair(issuerDid.keyPaths, seed, PrismDid.DEFAULT_REVOCATION_KEY_ID)
        val nodePayloadGenerator = NodePayloadGenerator(
            PrismDid.fromString(issuerDid.uriLongForm) as LongFormPrismDid,
            mapOf(PrismDid.DEFAULT_REVOCATION_KEY_ID to revocationKeyPair.privateKey)
        )
        val revokeInfo = nodePayloadGenerator.revokeCredentials(
            PrismDid.DEFAULT_REVOCATION_KEY_ID,
            Sha256Digest.fromHex(credential.operationHash),
            credential.batchId,
            // Pass empty array to revoke all credentials from the batch
            arrayOf(Sha256Digest.fromHex(credential.credentialHash))
        )
        val revokeOperationId = runBlocking {
            nodeAuthApi.revokeCredentials(
                revokeInfo.payload,
                PrismDid.fromString(issuerDid.uriCanonical).asCanonical(),
                PrismDid.DEFAULT_REVOCATION_KEY_ID,
                Sha256Digest.fromHex(credential.operationHash),
                credential.batchId,
                arrayOf(Sha256Digest.fromHex(credential.credentialHash))
            )
        }
        issuerDid.operationHash.add(revokeInfo.operationHash.hexValue)
        issuerDid.operationId.add(revokeOperationId.hexValue())
        credential.operationId.add(revokeOperationId.hexValue())
        return Pair(issuerDid, credential)
    }

    /**
     * Verify credential
     *
     * @return Verification result
     */
    fun verifyCredential(credential: Credential): List<String> {
        val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
        // Use encodeDefaults to generate empty siblings field on proof
        val format = Json { encodeDefaults = true }
        val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))

        return runBlocking {
            nodeAuthApi.verify(signed, proof).toMessageArray()
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
     * Grpc config
     *
     * @constructor Create empty Grpc config
     */
    class GrpcConfig {
        companion object {
            var protocol: String = System.getenv("PRISM_NODE_PROTOCOL") ?: "http"
            var host: String = System.getenv("PRISM_NODE_HOST") ?: ""
            var port: String = System.getenv("PRISM_NODE_PORT") ?: "50053"
            var token: String? = System.getenv("PRISM_NODE_TOKEN") ?: null
            fun options() = GrpcOptions(protocol, host, port.toInt(), token)
        }
    }
}
