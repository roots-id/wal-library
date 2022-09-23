package com.rootsid.wal.library.didcomm.model

import com.rootsid.wal.library.didcomm.common.DidCommDataTypes
import java.io.Serializable
import java.time.LocalDateTime

interface DidCommConnection: Serializable {
    val _id: String
    val invitationMsgId: String
    val invitationUrl: String?
    val alias: String
    val accept: DidCommDataTypes.Accept
    val state: DidCommDataTypes.ConnectionState
    val theirRole: DidCommDataTypes.TheirRole
    val invitationMode: DidCommDataTypes.InvitationMode
    val connectionProtocol: DidCommDataTypes.ConnectionProtocol
    val routingState: DidCommDataTypes.RoutingState
    val invitationKey: String
    val myDid: String?
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime
}



