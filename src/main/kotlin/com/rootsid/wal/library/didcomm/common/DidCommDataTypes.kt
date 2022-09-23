package com.rootsid.wal.library.didcomm.common

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue

sealed class DidCommDataTypes {
    companion object {
        inline fun <reified T, U> findByStorageRepresentation(source: U): T where T : Enum<T>,
                                                                                  T : StorageRepresentable<U> =
            enumValues<T>().first { it.value == source }
    }

    /**
     * Connection acceptance: manual or auto
     * Values: manual, auto
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class Accept(@JsonValue override val value: String) : StorageRepresentable<String> {
        MANUAL("manual"),
        AUTO("auto")
    }

    /**
     * Connection protocol used
     * Values: didcomm/2.0
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class ConnectionProtocol(@JsonValue override val value: String) : StorageRepresentable<String> {
        DIDCOMM_2_0("didcomm/2.0")
    }

    /**
     * Connection state using rfc23_state
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class ConnectionState(@JsonValue override val value: String) : StorageRepresentable<String> {
        START("start"),
        INVITATION_SENT("invitation-sent"),
        INVITATION_RECEIVED("invitation-received"),
        REQUEST_SENT("request-sent"),
        REQUEST_RECEIVED("request-received"),
        RESPONSE_SENT("response-sent"),
        RESPONSE_RECEIVED("response-received"),
        ABANDONED("abandoned"),
        COMPLETED("completed")
    }

    /**
     * Their role in the connection protocol
     * Values: invitee, requester, inviter, responder
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class TheirRole(@JsonValue override val value: String) : StorageRepresentable<String> {
        INVITEE("invitee"),
        INVITER("inviter"),
        REQUESTER("requester"),
        RESPONDER("responder")
    }

    /**
     * Invitation mode
     * Values: simple, multi
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class InvitationMode(@JsonValue override val value: String) : StorageRepresentable<String> {
        SIMPLE("simple"),
        MULTI("multi")
    }

    /**
     * Routing state of connection
     * Values: none, request, active, error
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class RoutingState(@JsonValue override val value: String) : StorageRepresentable<String> {
        NONE("none"),
        REQUEST("request"),
        ACTIVE("active"),
        ERROR("error")
    }
}
