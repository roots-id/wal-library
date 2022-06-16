package com.rootsid.wal.library.prism

import com.rootsid.wal.library.*
import com.rootsid.wal.library.prism.model.Did
import com.rootsid.wal.library.prism.model.DltDidUpdate
import com.rootsid.wal.library.prism.model.KeyPath
import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
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
import pbandk.ExperimentalProtoJson
import pbandk.json.encodeToJsonString

class Dlt {

    /**
     * Transaction id
     *
     * @param oid operation identifier
     * @return transaction Id
     */
    @OptIn(PrismSdkInternal::class)
    private fun transactionId(oid: AtalaOperationId): String {
        val node = NodeServiceCoroutine.Client(GrpcClient(GrpcConfig.options()))
        val response = runBlocking {
            node.GetOperationInfo(GetOperationInfoRequest(ByteArr(oid.value())))
        }
        return response.transactionId
    }

    /**
     * Wait for submission
     *
     * @param nodePublicApi PRISM node
     * @param operationId operation Identifier
     * @param action action associated with the operation (for traceability)
     * @return Log Entry
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
        println("Track the transaction in:\n- ${Config.TESTNET_URL}$tid\n")
        return BlockchainTxLogEntry(tid, action, "${Config.TESTNET_URL}$tid", description)
    }

    /**
     * Wait until confirmed
     *
     * @param nodePublicApi PRISM node
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
     * Derive key pair
     *
     * @param keyPairs List containing key path information
     * @param seed seed
     * @param keyId Id of the key to derive
     * @return Key pair
     */
    @OptIn(PrismSdkInternal::class)
    private fun deriveKeyPair(keyPairs: MutableList<KeyPath>, seed: ByteArray, keyId: String): ECKeyPair {
        val keyPathList = keyPairs.filter { it.keyId == keyId }
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
        return Did(didAlias, didIdx, unpublishedDid.asCanonical().did.toString(), unpublishedDid.did.toString(), "", keyPaths)
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
}
