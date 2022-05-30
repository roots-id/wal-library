package com.rootsid.wal.library

import java.util.UUID

fun createInvitation(createInvitationRequest: CreateInvitationRequest, autoAccept: Boolean, multiUse: Boolean): Invitation {
    val invitation = Invitation(
        "https://didcomm.org/out-of-band/2.0/invitation",
        UUID.randomUUID().toString(),
        createPeerDID(1, 1, "http://myendpoint.com", mutableListOf(), SecretResolver())
    )
    return invitation
}


/**


import com.rootsid.wal.library.createInvitation
import com.rootsid.wal.library.CreateInvitationRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val ir = CreateInvitationRequest("alias", null, "invitation to join")
val iv = createInvitation(ir, false, false)
val json = Json.encodeToString(iv)

json.toString()

**/
