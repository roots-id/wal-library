package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.didcomm.common.DidCommDataTypes
import com.rootsid.wal.library.didcomm.model.DidCommConnection
import com.rootsid.wal.library.utils.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.*

data class DidCommConnectionDocument(
    override val alias: String,
    override val invitationKey: String,
    override val accept: String = "manual",
    override val _id: String = UUID.randomUUID().toString(),
    override val invitationMsgId: String = UUID.randomUUID().toString(),
    override val state: DidCommDataTypes.ConnectionStatus = DidCommDataTypes.ConnectionStatus.INVITATION,
    override val theirRole: DidCommDataTypes.TheirRole = DidCommDataTypes.TheirRole.INVITEE,
    override val invitationMode: DidCommDataTypes.InvitationMode = DidCommDataTypes.InvitationMode.SIMPLE,
    override val connectionProtocol: DidCommDataTypes.ConnectionProtocol = DidCommDataTypes.ConnectionProtocol.DIDCOMM_2_0,
    override val routingState: DidCommDataTypes.RoutingState = DidCommDataTypes.RoutingState.NONE,
    @Serializable(with = LocalDateTimeSerializer::class) override val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class) override val updatedAt: LocalDateTime = LocalDateTime.now()
) : DidCommConnection
