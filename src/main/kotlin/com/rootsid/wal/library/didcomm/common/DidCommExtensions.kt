package com.rootsid.wal.library.didcomm.common

import org.didcommx.peerdid.DIDCommServicePeerDID
import org.didcommx.peerdid.DIDDocPeerDID
import org.didcommx.peerdid.PeerDID
import org.didcommx.peerdid.resolvePeerDID
import kotlin.streams.asStream


fun DIDDocPeerDID.getServiceEndpoint(protocol: DidCommDataTypes.ConnectionProtocol): String? =
    this.service?.asSequence()?.filterIsInstance<DIDCommServicePeerDID>()?.asStream()
        ?.filter { s -> s.accept.contains(protocol.value) }
        ?.findFirst()?.get()?.serviceEndpoint

fun PeerDID.getServiceEndpoint(
    protocol: DidCommDataTypes.ConnectionProtocol = DidCommDataTypes.ConnectionProtocol.DIDCOMM_2_0
): String? = this.to().getServiceEndpoint(protocol)

fun PeerDID.to(): DIDDocPeerDID = DIDDocPeerDID.fromJson(resolvePeerDID(this))
