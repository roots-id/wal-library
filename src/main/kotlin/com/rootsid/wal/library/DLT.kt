package com.rootsid.wal.library

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.VerificationResult
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.credentials.json.JsonBasedCredential
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.crypto.derivation.MnemonicCode
import io.iohk.atala.prism.crypto.keys.ECKeyPair
import io.iohk.atala.prism.identity.*
import io.iohk.atala.prism.protos.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pbandk.ByteArr

/**
 * Transaction id
 *
 * @param oid operation identifier
 * @return transaction Id
 */
@OptIn(PrismSdkInternal::class)
private fun transactionId(oid: AtalaOperationId): String {
    val node = NodeServiceCoroutine.Client(GrpcClient(GrpcConfig.options))
    val response = runBlocking {
        node.GetOperationInfo(GetOperationInfoRequest(ByteArr(oid.value())))
    }
    return response.transactionId
}

/**
 * Wait until confirmed
 *
 * @param nodePublicApi PRISM node
 * @param operationId operation Identifier
 */
private fun waitUntilConfirmed(nodePublicApi: NodePublicApi, operationId: AtalaOperationId) {
    var tid = ""
    var status = runBlocking {
        nodePublicApi.getOperationInfo(operationId).status
    }
    while (status != AtalaOperationStatus.CONFIRMED_AND_APPLIED &&
        status != AtalaOperationStatus.CONFIRMED_AND_REJECTED
    ) {
        if (status == AtalaOperationStatus.AWAIT_CONFIRMATION && tid.isEmpty()) {
            tid = transactionId(operationId)
            println("Track the transaction in:\n- ${Config.TESTNET_URL}$tid\n")
        }
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
private fun deriveKeyPair(keyPairs: MutableList<KeyPair>, seed: ByteArray, keyId: String): ECKeyPair {
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
 * New wallet
 *
 * @param name Wallet name
 * @param mnemonic mnemonic, leave empty to generate a random one
 * @param passphrase passphrase
 * @return a new wallet
 */
fun newWallet(name: String, mnemonic: String, passphrase: String): Wallet {
    return if (mnemonic.isBlank()) {
        Wallet(name, KeyDerivation.randomMnemonicCode().words, passphrase)
    } else {
        try {
            val mnemonicList = mnemonic.split(Config.MNEMONIC_SEPARATOR)
                .map { it.trim() }
            KeyDerivation.binarySeed(MnemonicCode(mnemonicList), passphrase)
            Wallet(name, mnemonicList, passphrase)
        } catch (e: Exception) {
            throw Exception("Invalid mnemonic phrase")
        }
    }
}

/**
 * New did
 *
 * @param wallet Wallet to store the DID
 * @param didAlias Alias for the new DID
 * @param issuer If true issuing and holder keys are included, otherwise only a master key pair is added
 * @return updated wallet
 */
@OptIn(PrismSdkInternal::class)
fun newDid(wallet: Wallet, didAlias: String, issuer: Boolean): Wallet {
    // To keep DID index sequential
    val didIdx = wallet.dids.size
    val keyPairs = mutableListOf<KeyPair>()
    val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
    val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(
        seed, didIdx, MasterKeyUsage, 0
    )
    val masterKeyPairData = KeyPair(
        PrismDid.DEFAULT_MASTER_KEY_ID,
        KeyUsage.MASTER_KEY.value,
        didIdx,
        MasterKeyUsage.derivationIndex(),
        0,
        masterKeyPair.privateKey.getHexEncoded(),
        masterKeyPair.publicKey.getHexEncoded()
    )
    keyPairs.add(masterKeyPairData)

    val unpublishedDid = if (issuer) {
        val issuingKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed, didIdx, IssuingKeyUsage, 0
        )
        val revocationKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed, didIdx, RevocationKeyUsage, 0
        )
        val issuingKeyPairData = KeyPair(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            KeyUsage.ISSUING_KEY.value,
            didIdx,
            IssuingKeyUsage.derivationIndex(),
            0,
            issuingKeyPair.privateKey.getHexEncoded(),
            issuingKeyPair.publicKey.getHexEncoded()
        )
        val revocationKeyPairData = KeyPair(
            PrismDid.DEFAULT_REVOCATION_KEY_ID,
            KeyUsage.REVOCATION_KEY.value,
            didIdx,
            RevocationKeyUsage.derivationIndex(),
            0,
            revocationKeyPair.privateKey.getHexEncoded(),
            revocationKeyPair.publicKey.getHexEncoded()
        )
        keyPairs.add(issuingKeyPairData)
        keyPairs.add(revocationKeyPairData)

        PrismDid.buildExperimentalLongFormFromKeys(
            masterKeyPair.publicKey, issuingKeyPair.publicKey, revocationKeyPair.publicKey
        )
    } else {
        PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)
    }
    wallet.dids.add(
        DID(didAlias, didIdx, unpublishedDid.asCanonical().did.toString(), unpublishedDid.did.toString(), "", keyPairs)
    )
    return wallet
}

/**
 * Get did document
 *
 * @param wallet Wallet containing the DID
 * @param didAlias Alias of the DID
 * @return DID document
 */
fun getDidDocument(wallet: Wallet, didAlias: String): PrismDidDataModel {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val did = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val prismDid = try {
            PrismDid.fromString(did.uriLongForm)
        } catch (e: Exception) {
            throw Exception("not a Prism DID: $did")
        }
        return runBlocking { nodeAuthApi.getDidDocument(prismDid) }
    } else {
        throw NoSuchElementException("DID '$didAlias' not found.")
    }
}

/**
 * Publish did
 *
 * @param wallet Wallet contining the DID
 * @param didAlias Alias of the DID
 * @return updated wallet
 */
fun publishDid(wallet: Wallet, didAlias: String): Wallet {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val did = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val prismDid = PrismDid.fromString(did.uriLongForm)
        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
        val masterKeyPair = deriveKeyPair(did.keyPairs, seed, PrismDid.DEFAULT_MASTER_KEY_ID)
        val issuingKeyPair = deriveKeyPair(did.keyPairs, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)
        val revocationKeyPair = deriveKeyPair(did.keyPairs, seed, PrismDid.DEFAULT_REVOCATION_KEY_ID)

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
        waitUntilConfirmed(nodeAuthApi, createDidOperationId)

        val response = runBlocking { nodeAuthApi.getOperationInfo(createDidOperationId) }
        require(response.status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected publishing to be applied: ${response.statusDetails}"
        }
        did.operationHash = createDidInfo.operationHash.hexValue
        return wallet
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Add key
 *
 * @param wallet Wallet containing the DID
 * @param didAlias Alias of DID where the key will be added
 * @param keyId Key identifier for the new key
 * @param keyTypeValue Type of key (master, issuing or revocation)
 * @return updated wallet
 */
@OptIn(PrismSdkInternal::class)
fun addKey(wallet: Wallet, didAlias: String, keyId: String, keyTypeValue: Int): Wallet {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val did = didList[0]
        val keyIdx = did.keyPairs.filter { it.keyTypeValue == keyTypeValue }.size
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)

        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
        // TODO: masterKey index 0 may be revoked, do something to indicate the currently valid masterKey
        val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, did.didIdx, MasterKeyUsage, 0)
        val newKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed,
            did.didIdx,
            PublicKeyUsage.fromProto(KeyUsage.fromValue(keyTypeValue)),
            keyIdx
        )
        val keyUsage = KeyUsage.fromValue(keyTypeValue)
        val newKeyPairData = KeyPair(
            keyId,
            keyTypeValue,
            did.didIdx,
            PublicKeyUsage.fromProto(keyUsage).derivationIndex(),
            keyIdx,
            newKeyPair.privateKey.getHexEncoded(),
            newKeyPair.publicKey.getHexEncoded()
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
        waitUntilConfirmed(nodeAuthApi, updateDidOperationId)

        val response = runBlocking { nodeAuthApi.getOperationInfo(updateDidOperationId) }
        require(response.status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected did to be updated: $response"
        }
        // Update DID last operation hash
        did.operationHash = updateDidInfo.operationHash.hexValue
        did.keyPairs.add(newKeyPairData)
        return wallet
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Revoke key
 *
 * @param wallet Wallet containing the DID
 * @param didAlias Alias of DID containing the key
 * @param keyId Identifier of the key to be revoked
 * @return updated wallet
 */
fun revokeKey(wallet: Wallet, didAlias: String, keyId: String): Wallet {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val did = didList[0]
        val keyPairList = did.keyPairs.filter { it.keyId == keyId }
        if (keyPairList.isNotEmpty()) {
            val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)

            // Key pairs to get private keys
            val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
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
            waitUntilConfirmed(nodeAuthApi, updateDidOperationId)

            val response = runBlocking { nodeAuthApi.getOperationInfo(updateDidOperationId) }
            require(response.status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
                "expected did to be updated: $response"
            }
            // Key revocation flag
            keyPairList[0].revoked = true
            // Update DID last operation hash
            did.operationHash = updateDidInfo.operationHash.hexValue
            return wallet
        } else {
            throw NoSuchElementException("Key identifier '$keyId' not found.")
        }
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Issue credential
 *
 * @param wallet Wallet issuing the credential
 * @param didAlias Issuer DID
 * @param issuedCredential Credential data
 * @return updated wallet
 */
fun issueCredential(wallet: Wallet, didAlias: String, issuedCredential: IssuedCredential): Wallet {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val issuerDid = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val claims = mutableListOf<CredentialClaim>()
        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
        val issuingKeyPair = deriveKeyPair(issuerDid.keyPairs, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)

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
        issuedCredential.issuingDidAlias = didAlias
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
        waitUntilConfirmed(nodeAuthApi, issueCredentialsOperationId)

        val response = runBlocking { nodeAuthApi.getOperationInfo(issueCredentialsOperationId) }
        require(response.status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected credentials to be issued"
        }
        // Update DID last operation hash
        issuerDid.operationHash = credentialsInfo.operationHash.hexValue
        wallet.issuedCredentials.add(issuedCredential)
        return wallet
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Revoke credential
 *
 * @param wallet Wallet containing the credential
 * @param credentialAlias Alias of credential to revoke
 * @return Updated wallet
 */
fun revokeCredential(wallet: Wallet, credentialAlias: String): Wallet {
    val credentials = wallet.issuedCredentials.filter { it.alias == credentialAlias }
    if (credentials.isNotEmpty()) {
        val credential = credentials[0]
        val didList = wallet.dids.filter { it.alias == credential.issuingDidAlias }
        if (didList.isNotEmpty()) {
            val issuerDid = didList[0]
            val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
            // Key pairs to get private keys
            val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
            val revocationKeyPair = deriveKeyPair(issuerDid.keyPairs, seed, PrismDid.DEFAULT_REVOCATION_KEY_ID)
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
            waitUntilConfirmed(nodeAuthApi, revokeOperationId)

            val status = runBlocking { nodeAuthApi.getOperationInfo(revokeOperationId).status }
            require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
                "expected credential to be revoked"
            }
            credential.revoked = true
            return wallet
        } else {
            throw NoSuchElementException("Issuing DID not found.")
        }
    } else {
        throw NoSuchElementException("Credential '$credentialAlias' not found.")
    }
}

/**
 * Verify issued credential
 *
 * @param wallet Wallet containing the credential
 * @param credentialAlias Alias of Credential to verify
 * @return Verification result
 */
// TODO: refactor to a single verifyCredential function
fun verifyIssuedCredential(wallet: Wallet, credentialAlias: String): VerificationResult {
    val credentials = wallet.issuedCredentials.filter { it.alias == credentialAlias }
    if (credentials.isNotEmpty()) {
        val credential = credentials[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
        // Use encodeDefaults to generate empty siblings field on proof
        val format = Json { encodeDefaults = true }
        val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))

        return runBlocking {
            nodeAuthApi.verify(signed, proof)
        }
    } else {
        throw Exception("Credential '$credentialAlias' not found.")
    }
}

/**
 * Verify imported credential
 *
 * @param wallet Wallet containing the credential
 * @param credentialAlias Alias of credential to verify
 * @return Verification result
 */
// TODO: refactor to a single verifyCredential function
fun verifyImportedCredential(wallet: Wallet, credentialAlias: String): VerificationResult {
    val credentials = wallet.importedCredentials.filter { it.alias == credentialAlias }
    if (credentials.isNotEmpty()) {
        val credential = credentials[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
        // Use encodeDefaults to generate empty siblings field on proof
        val format = Json { encodeDefaults = true }
        val proof = MerkleInclusionProof.decode(format.encodeToString(credential.verifiedCredential.proof))

        return runBlocking {
            nodeAuthApi.verify(signed, proof)
        }
    } else {
        throw Exception("Credential '$credentialAlias' not found.")
    }
}

/**
 * Grpc config
 *
 * @constructor Create empty Grpc config
 */
class GrpcConfig {
    companion object {
        private val host: String = System.getenv("PRISM_NODE_HOST")
        private val port: String = System.getenv("PRISM_NODE_PORT") ?: "50053"
        val options = GrpcOptions("https", host, port.toInt())
    }
}
