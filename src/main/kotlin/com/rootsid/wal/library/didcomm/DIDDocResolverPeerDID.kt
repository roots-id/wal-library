package com.rootsid.wal.library.didcomm

import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.diddoc.DIDCommService
import org.didcommx.didcomm.diddoc.DIDDoc
import org.didcommx.didcomm.diddoc.DIDDocResolver
import org.didcommx.didcomm.diddoc.VerificationMethod
import org.didcommx.didcomm.utils.toJson
import org.didcommx.peerdid.DIDCommServicePeerDID
import org.didcommx.peerdid.DIDDocPeerDID
import org.didcommx.peerdid.VerificationMaterialFormatPeerDID
import org.didcommx.peerdid.resolvePeerDID
import java.util.*

class DIDDocResolverPeerDID : DIDDocResolver {

    override fun resolve(did: String): Optional<DIDDoc> {
        // request DID Doc in JWK format
        val didDocJson = resolvePeerDID(did, format = VerificationMaterialFormatPeerDID.JWK)
        val didDoc = DIDDocPeerDID.fromJson(didDocJson)

        didDoc.keyAgreement
        return Optional.ofNullable(
            DIDDoc(
                did = did,
                keyAgreements = didDoc.agreementKids,
                authentications = didDoc.authenticationKids,
                verificationMethods = (didDoc.authentication + didDoc.keyAgreement).map {
                    VerificationMethod(
                        id = it.id,
                        type = VerificationMethodType.JSON_WEB_KEY_2020,
                        controller = it.controller,
                        verificationMaterial = VerificationMaterial(
                            format = VerificationMaterialFormat.JWK,
                            value = toJson(it.verMaterial.value)
                        )
                    )
                },
                didCommServices = didDoc.service?.mapNotNull {
                    when (it) {
                        is DIDCommServicePeerDID ->
                            DIDCommService(
                                id = it.id,
                                serviceEndpoint = it.serviceEndpoint,
                                routingKeys = it.routingKeys,
                                accept = it.accept
                            )
                        else -> null
                    }
                }
                    ?: emptyList()
            )
        )
    }
}
