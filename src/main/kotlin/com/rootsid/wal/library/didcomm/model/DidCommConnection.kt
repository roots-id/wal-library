package com.rootsid.wal.library.didcomm.model

import com.rootsid.wal.library.didcomm.common.DidCommDataTypes
import java.io.Serializable
import java.time.LocalDateTime

interface DidCommConnection: Serializable {
    val _id: String
    val invitationMsgId: String
    val alias: String
    val accept: String
    val state: DidCommDataTypes.ConnectionStatus
    val theirRole: DidCommDataTypes.TheirRole
    val invitationMode: DidCommDataTypes.InvitationMode
    val connectionProtocol: DidCommDataTypes.ConnectionProtocol
    val routingState: DidCommDataTypes.RoutingState
    val invitationKey: String
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime
}



