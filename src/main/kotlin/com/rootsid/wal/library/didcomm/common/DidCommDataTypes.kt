package com.rootsid.wal.library.didcomm.common

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue

sealed class DidCommDataTypes {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class ConnectionProtocol(@JsonValue override val value: String) : StorageRepresentable<String> {
        DIDCOMM_2_0("didcomm/2.0")
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class ConnectionStatus(@JsonValue override val value: String) : StorageRepresentable<String> {
        INVITATION("invitation"),
        ACTIVE("active"),
        REQUEST("request"),
        RESPONSE("response"),
        COMPLETED("completed")
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class TheirRole(@JsonValue override val value: String) : StorageRepresentable<String> {
        INVITEE("invitee"),
        INVITER("inviter")
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class InvitationMode(@JsonValue override val value: String) : StorageRepresentable<String> {
        SIMPLE("simple"),
        MULTI("multi")
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum class RoutingState(@JsonValue override val value: String) : StorageRepresentable<String> {
        NONE("none")
    }
}

inline fun <reified T, U> findBy(source: U): T where T : Enum<T>,
                                                      T : StorageRepresentable<U> =
    enumValues<T>().first { it.value == source }
