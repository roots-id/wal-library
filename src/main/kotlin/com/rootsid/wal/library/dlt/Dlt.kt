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
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.api.node.PrismDidState
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.EC
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.crypto.keys.ECKeyPair
import io.iohk.atala.prism.identity.*
import io.iohk.atala.prism.protos.*
import io.ipfs.multibase.Base58
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import pbandk.ByteArr
import pbandk.json.encodeToJsonString

private const val TESTNET_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction?id="

class Dlt {
    /**
     * Use this function to get the blockchain transaction ID associated with an operationID
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

    fun getDidPublishOperationInfo(did: Did): AtalaOperationStatusEnum {
        val operationId = did.publishedOperationId

        if("" == operationId) {
            throw Exception("Unable to find operation information because operation id was empty.")
        }

        val operationInfo = getOperationInfo(AtalaOperationId.fromHex(operationId))
        operationInfo.transactionId?.let { BlockchainTxLogEntry(it, BlockchainTxAction.PUBLISH_DID,
            "${TESTNET_URL}$it", did.alias) }

        return operationInfo.status
    }

    /**
     * Get operation information using the operationId
     *
     * @param operationId operation identifier
     * @return operation information from the blockchain transaction
     */
    private fun getOperationInfo(operationId: AtalaOperationId): AtalaOperationInfo {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())

        return runBlocking { nodeAuthApi.getOperationInfo(operationId) }
    }

    /**
     * Waits until the operationId status changes from [AtalaOperationStatus.PENDING_SUBMISSION] to [AtalaOperationStatus.AWAIT_CONFIRMATION]
     *
     * @param nodePublicApi PRISM node request handler
     * @param operationId operation Identifier
     * @param action action associated with the operation (for traceability)
     * @param description additional details
     * @return Log Entry containing details of the operation and the blockchain transaction
     */
    private fun waitForSubmission(nodePublicApi: NodePublicApi, operationId: AtalaOperationId, action: BlockchainTxAction, description: String): BlockchainTxLogEntry {
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
        val tid = transactionId(operationId)
        println("Track the transaction in:\n- ${Constant.TESTNET_URL}$tid\n")
        return BlockchainTxLogEntry(tid, action, "${Constant.TESTNET_URL}$tid", description)
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
            seed, didIdx, MasterKeyUsage, 0
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
                seed, didIdx, IssuingKeyUsage, 0
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
                seed, didIdx, RevocationKeyUsage, 0
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
                masterKeyPair.publicKey, issuingKeyPair.publicKey, revocationKeyPair.publicKey
            )
        } else {
            PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)
        }
        return Did(didAlias, didIdx, unpublishedDid.asCanonical().did.toString(), unpublishedDid.did.toString(), keyPaths)
    }

    /**
     * Get did state
     *
     * @param did did:prism string
     * @return DID state
     */
    fun getDidState(did: String): PrismDidState {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
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
     * @param did did:prism string
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
     * Get did document
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

        var didDocW3C = mutableMapOf<String, JsonElement>(
            "@context" to JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/did/v1"))),
            "id" to JsonPrimitive(did),
            "assertionMethod" to JsonArray(listOf(JsonPrimitive(did + "#master0"))),
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
     * Publish did
     *
     * @param did did to be published
     * @param didAlias seed to generate the keys
     * @return DltDidUpdate (operationId, Updated Did)
     */
    fun publishDid(did: Did, seed: ByteArray): DltDidUpdate {
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
        val prismDid = PrismDid.fromString(did.uriLongForm)
        // Key pairs to get private keys
        val masterKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_MASTER_KEY_ID)
        val issuingKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)
        val revocationKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_REVOCATION_KEY_ID)
        // TODO: refactor to allow publishing of DIDs with other key pairs arrangements (e.g. with no revocation key, multiple master keys, etc.)
        val nodePayloadGenerator = NodePayloadGenerator(
            prismDid as LongFormPrismDid,
            mapOf(
                PrismDid.DEFAULT_MASTER_KEY_ID to masterKeyPair.privateKey,
                PrismDid.DEFAULT_ISSUING_KEY_ID to issuingKeyPair.privateKey,
                PrismDid.DEFAULT_REVOCATION_KEY_ID to revocationKeyPair.privateKey
            )
        )
        val createDidInfo = nodePayloadGenerator.createDid()
        val createDidOperationId = runBlocking {
            nodeAuthApi.createDid(
                createDidInfo.payload,
                prismDid,
                PrismDid.DEFAULT_MASTER_KEY_ID
            )
        }
        did.operationHash = createDidInfo.operationHash.hexValue
        return DltDidUpdate(createDidOperationId.hexValue(), did)
    }

    /**
     * Add key
     *
     * @param did did to add key to
     * @param seed seed to generate the keys
     * @param keyId key id
     * @param keyTypeValue key type
     * @return DltDidUpdate (operationId, Updated Did)
     */
    @OptIn(PrismSdkInternal::class)
    fun addKey(did: Did, seed: ByteArray, keyId: String, keyTypeValue: Int): DltDidUpdate {

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
            previousHash = Sha256Digest.fromHex(did.operationHash),
            masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
            keysToAdd = arrayOf(newKeyInfo)
        )
        val updateDidOperationId = runBlocking {
            nodeAuthApi.updateDid(
                payload = updateDidInfo.payload,
                did = PrismDid.fromString(did.uriCanonical).asCanonical(),
                masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                previousOperationHash = Sha256Digest.fromHex(did.operationHash),
                keysToAdd = arrayOf(newKeyInfo),
                keysToRevoke = arrayOf()
            )
        }
        did.operationHash = updateDidInfo.operationHash.hexValue
        did.keyPaths.add(newKeyPairData)
        return DltDidUpdate(updateDidOperationId.hexValue(), did)
    }

    /**
     * Revoke key
     *
     * @param did did containing the key to be revoked
     * @param seed seed to generate the keys
     * @param keyId key identifier to be revoked
     * @return DltDidUpdate (operationId, Updated Did)
     */
    fun revokeKey(did: Did, seed: ByteArray, keyId: String): DltDidUpdate {

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
                previousHash = Sha256Digest.fromHex(did.operationHash),
                masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                keysToRevoke = arrayOf(keyId)
            )
            val updateDidOperationId = runBlocking {
                nodeAuthApi.updateDid(
                    payload = updateDidInfo.payload,
                    did = PrismDid.fromString(did.uriCanonical).asCanonical(),
                    masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                    previousOperationHash = Sha256Digest.fromHex(did.operationHash),
                    keysToAdd = arrayOf(),
                    keysToRevoke = arrayOf(keyId)
                )
            }
            // Key revocation flag
            keyPairList[0].revoked = true
            // Update DID last operation hash
            did.operationHash = updateDidInfo.operationHash.hexValue
            return DltDidUpdate(updateDidOperationId.hexValue(), did)
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
     * @return DltDidUpdate (operationId, Updated Did)
     */
    fun issueCredential(issuerDid: Did, seed: ByteArray, issuedCredential: IssuedCredential): DltDidUpdate {
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
        issuerDid.operationHash = credentialsInfo.operationHash.hexValue
        return DltDidUpdate(issueCredentialsOperationId.hexValue(), issuerDid)
    }

    /**
     * Revoke credential
     *
     * @param credential credential to be revoked
     * @param issuerDid issuer did
     * @param seed seed to generate the keys
     * @return DltDidUpdate (operationId, Updated Did)
     */
    // TODO: Check the Return type
    fun revokeCredential(credential: IssuedCredential, issuerDid: Did, seed: ByteArray): DltDidUpdate {
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
        credential.revoked = true
        return DltDidUpdate(revokeOperationId.hexValue(), issuerDid)
    }

    // TODO: REFACTOR PENDING ON FUNCTIONS BELOW

    /**
     * Verify issued credential
     *
     * @param wallet Wallet containing the credential
     * @param credentialAlias Alias of Credential to verify
     * @return Verification result
     */
    // TODO: refactor to a single verifyCredential function
//    fun verifyIssuedCredential(wallet: Wallet, credentialAlias: String): List<String> {
//        val credentials = wallet.issuedCredentials.filter { it.alias == credentialAlias }
//        if (credentials.isNotEmpty()) {
//            val credential = credentials[0]
//            val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
//            val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
//            // Use encodeDefaults to generate empty siblings field on proof
//            val format = Json { encodeDefaults = true }
//            val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))
//
//            return runBlocking {
//                nodeAuthApi.verify(signed, proof).toMessageArray()
//            }
//        } else {
//            throw Exception("Credential '$credentialAlias' not found.")
//        }
//    }

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
//    fun verifyImportedCredential(wallet: Wallet, credentialAlias: String): List<String> {
//        val credentials = wallet.importedCredentials.filter { it.alias == credentialAlias }
//        if (credentials.isNotEmpty()) {
//            val credential = credentials[0]
//            val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options())
//            val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
//            // Use encodeDefaults to generate empty siblings field on proof
//            val format = Json { encodeDefaults = true }
//            val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))
//
//            return runBlocking {
//                nodeAuthApi.verify(signed, proof).toMessageArray()
//            }
//        } else {
//            throw Exception("Credential '$credentialAlias' not found.")
//        }
//    }

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
}
