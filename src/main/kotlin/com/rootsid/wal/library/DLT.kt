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
import io.iohk.atala.prism.identity.LongFormPrismDid
import io.iohk.atala.prism.identity.PrismDid
import io.iohk.atala.prism.identity.PrismDidDataModel
import io.iohk.atala.prism.identity.PrismKeyType
import io.iohk.atala.prism.protos.GetOperationInfoRequest
import io.iohk.atala.prism.protos.GrpcClient
import io.iohk.atala.prism.protos.GrpcOptions
import io.iohk.atala.prism.protos.NodeServiceCoroutine
import kotlinx.coroutines.runBlocking
import pbandk.ByteArr

/**
 * Transaction id
 *
 * @param oid
 * @return
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
 * @param nodePublicApi
 * @param operationId
 */
private fun waitUntilConfirmed(nodePublicApi: NodePublicApi, operationId: AtalaOperationId) {
    var tid = ""
    var status = runBlocking {
        nodePublicApi.getOperationStatus(operationId)
    }
    while (status != AtalaOperationStatus.CONFIRMED_AND_APPLIED &&
        status != AtalaOperationStatus.CONFIRMED_AND_REJECTED
    ) {
        if (status == AtalaOperationStatus.AWAIT_CONFIRMATION && tid.isEmpty()) {
            tid = transactionId(operationId)
            println("Track the transaction in:\n- ${Constant.TESTNET_URL}$tid\n")
        }
        println("Current operation status: ${AtalaOperationStatus.asString(status)}\n")
        Thread.sleep(10000)
        status = runBlocking {
            nodePublicApi.getOperationStatus(operationId)
        }
    }
}

/**
 * Derive key pair
 *
 * @param keyPaths
 * @param seed
 * @param keyId
 * @return
 */
private fun deriveKeyPair(keyPaths: MutableList<KeyPath>, seed: ByteArray, keyId: String): ECKeyPair {
    val keyPathList = keyPaths.filter { it.keyId == keyId }
    if (keyPathList.isNotEmpty()) {
        val keyPath = keyPathList[0]
        return KeyGenerator.deriveKeyFromFullPath(seed, keyPath.didIdx, keyPath.keyType, keyPath.keyIdx)
    } else {
        throw NoSuchElementException("Key ID '$keyId' not found.")
    }
}

/**
 * New wallet
 *
 * @param name
 * @param mnemonic
 * @param passphrase
 * @return
 */
fun newWallet(name: String, mnemonic: String, passphrase: String): Wallet {
    return if (mnemonic.isBlank()) {
        Wallet(name, KeyDerivation.randomMnemonicCode().words, passphrase)
    } else {
        try {
            val mnemonicList = mnemonic.split(Constant.MNEMONIC_SEPARATOR)
                .map { it.trim() }
            KeyDerivation.binarySeed(MnemonicCode(mnemonicList), passphrase)
            Wallet(name, mnemonicList, passphrase)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid mnemonic phrase")
        }
    }
}

/**
 * New did
 *
 * @param wallet
 * @param didAlias
 * @param issuer
 * @return
 */
fun newDid(wallet: Wallet, didAlias: String, issuer: Boolean): Wallet {
    // To keep DID index sequential
    val didIdx = wallet.dids.size
    val keyPaths = mutableListOf<KeyPath>()
    val masterKeyPathData = KeyPath(PrismDid.DEFAULT_MASTER_KEY_ID, didIdx, PrismKeyType.MASTER_KEY, 0)

    val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
    val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(
        seed, masterKeyPathData.didIdx, masterKeyPathData.keyType, masterKeyPathData.keyIdx
    )
    keyPaths.add(masterKeyPathData)

    val unpublishedDid = if (issuer) {
        val issuingKeyPathData = KeyPath(PrismDid.DEFAULT_ISSUING_KEY_ID, didIdx, PrismKeyType.ISSUING_KEY, 0)
        val revocationKeyPathData = KeyPath(PrismDid.DEFAULT_REVOCATION_KEY_ID, didIdx, PrismKeyType.REVOCATION_KEY, 0)

        keyPaths.add(issuingKeyPathData)
        keyPaths.add(revocationKeyPathData)

        val issuingKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed, issuingKeyPathData.didIdx, issuingKeyPathData.keyType, issuingKeyPathData.keyIdx
        )
        val revocationKeyPair = KeyGenerator.deriveKeyFromFullPath(
            seed, revocationKeyPathData.didIdx, revocationKeyPathData.keyType, revocationKeyPathData.keyIdx
        )
        PrismDid.buildExperimentalLongFormFromKeys(
            masterKeyPair.publicKey, issuingKeyPair.publicKey, revocationKeyPair.publicKey
        )
    } else {
        PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)
    }
    wallet.dids.add(
        DID(didAlias, didIdx, unpublishedDid.asCanonical().did.toString(), unpublishedDid.did.toString(), "", keyPaths)
    )
    return wallet
}

/**
 * Get did document
 *
 * @param wallet
 * @param didAlias
 * @return
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
        println("trying to retrieve document for $did\n")
        try {
            val model = runBlocking { nodeAuthApi.getDidDocument(prismDid) }
            println("Public Keys size: ${model.publicKeys.size}\n")
            println("Model: ${model.didDataModel}\n")
            return model
        } catch (e: Exception) {
            throw NoSuchElementException("DID '$didAlias' not found.")
        }
    } else {
        throw NoSuchElementException("DID '$didAlias' not found.")
    }
}

/**
 * Publish did
 *
 * @param wallet
 * @param didAlias
 * @return
 */
fun publishDid(wallet: Wallet, didAlias: String): Wallet {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val did = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val prismDid = PrismDid.fromString(did.uriLongForm)
        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
        val masterKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_MASTER_KEY_ID)
        val issuingKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)
        val revocationKeyPair = deriveKeyPair(did.keyPaths, seed, PrismDid.DEFAULT_REVOCATION_KEY_ID)

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

        val status = runBlocking { nodeAuthApi.getOperationStatus(createDidOperationId) }
        require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected publishing to be applied"
        }
        did.operationHash = createDidInfo.operationHash.hexValue
        println("DID published")
        return wallet
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Issue credential
 *
 * @param wallet
 * @param didAlias
 * @param credential
 * @return
 */
fun issueCredential(wallet: Wallet, didAlias: String, credential: Credential): Pair<Wallet, Credential> {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val issuerDid = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        val claims = mutableListOf<CredentialClaim>()
        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
        val issuingKeyPair = deriveKeyPair(issuerDid.keyPaths, seed, PrismDid.DEFAULT_ISSUING_KEY_ID)

        claims.add(credential.claim.toCredentialClaim())

        val nodePayloadGenerator = NodePayloadGenerator(
            PrismDid.fromString(issuerDid.uriLongForm) as LongFormPrismDid,
            mapOf(PrismDid.DEFAULT_ISSUING_KEY_ID to issuingKeyPair.privateKey)
        )
        val credentialsInfo = nodePayloadGenerator.issueCredentials(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            claims.toTypedArray()
        )
        // credential batchId and hash are required for revocation
        credential.batchId = credentialsInfo.batchId.id
        val info = credentialsInfo.credentialsAndProofs[0]
        credential.credentialHash = info.signedCredential.hash().hexValue
        credential.operationHash = credentialsInfo.operationHash.hexValue
        credential.verifiedCredential = VerifiedCredential(
            info.signedCredential.canonicalForm,
            info.inclusionProof.encode()
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

        val status = runBlocking { nodeAuthApi.getOperationStatus(issueCredentialsOperationId) }
        require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected credentials to be issued"
        }
        // Update DID last operation hash
        issuerDid.operationHash = credentialsInfo.operationHash.hexValue
        return Pair(wallet, credential)
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

fun revokeCredential(wallet: Wallet, didAlias: String, credential: Credential) {
    val didList = wallet.dids.filter { it.alias == didAlias }
    if (didList.isNotEmpty()) {
        val issuerDid = didList[0]
        val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
        // Key pairs to get private keys
        val seed = KeyDerivation.binarySeed(MnemonicCode(wallet.mnemonic), wallet.passphrase)
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
        waitUntilConfirmed(nodeAuthApi, revokeOperationId)

        val status = runBlocking { nodeAuthApi.getOperationStatus(revokeOperationId) }
        require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
            "expected credential to be revoked"
        }
        // Update DID last operation hash TODO: Ask IOG why there is no operation hash for revocation
        // issuerDid.hash = revokeInfo.operationHash.hexValue
    } else {
        throw NoSuchElementException("DID alias '$didAlias' not found.")
    }
}

/**
 * Verify credential
 *
 * @param credential
 * @return
 */
fun verifyCredential(credential: Credential): VerificationResult {
    val nodeAuthApi = NodeAuthApiImpl(GrpcConfig.options)
    val signed = JsonBasedCredential.fromString(credential.verifiedCredential.encodedSignedCredential)
    val proof = MerkleInclusionProof.decode(credential.verifiedCredential.proof)

    return runBlocking {
        nodeAuthApi.verify(signed, proof)
    }
}

class GrpcConfig {
    companion object {
        private val host: String = System.getenv("PRISM_NODE_HOST")
        private val port: String = System.getenv("PRISM_NODE_PORT") ?: "50053"
        val options = GrpcOptions("https", host, port.toInt())
    }
}
