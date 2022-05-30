package com.rootsid.wal.library

import kotlinx.serialization.Serializable

// TODO: Remember to use this json deserializer configuration
// val jsonFormat = Json { prettyPrint = true; encodeDefaults = true ; ignoreUnknownKeys = true}

// {
//    "accept": "manual", / "auto"
//    "connection_id": "7f94eb4e-6032-46f1-bc6a-9e4dde5a210c",
//    "invitation_msg_id": "9d66a1a3-263c-40e1-812f-10855d48d045",
//    "rfc23_state": "invitation-sent",
//    "state": "invitation",
//    "their_role": "invitee",
//    "updated_at": "2022-05-03T04:08:29.187527Z",
//    "invitation_mode": "multi", / "once
//    "alias": "Barry-multiuse",
//    "routing_state": "none",
//    "connection_protocol": "didexchange/1.0",
//    "invitation_key": "oEPBV6LJtQ3nojYxdn7gmPtrYTwj8BeYvaJw4jwjeGP",
//    "created_at": "2022-05-03T04:08:29.187527Z"
// }

data class Connection(
    // "manual", / "auto"
    val accept: String,
    val connection_id: String,
    val invitation_msg_id: String,
    val rfc23_state: String,
    val their_role: String,
    val updated_at: String,
    // "multi", / "once
    val invitation_mode: String,
    val alias: String?,
    val routing_state: String,
    val connection_protocol: String,
    val created_at: String
//    val invitation_key: String,
//    val state: String,
)

@Serializable
data class CreateInvitationRequest(
    // Local alias
    val alias: String? = null,
    val metadata: Map<String, String>? = null,
    // Display name for the invitation
    val my_label: String? = null
//    val attachments: Map<String, String>? = null,
//    val handshake_protocols: List<String>? = null
)

@Serializable
data class Invitation(
    val type: String,
    val id: String,
    val from: String
//    val body: Body? = null
//    val attachments: List<Attachment>?
)

@Serializable
data class Body(
    val goal_code: String,
    val goal: String,
    val accept: List<String>
)

data class Attachment(
    val id: String,
    val mime_type: String,
    val data: Data?
)

data class Data(
    val json: String
)