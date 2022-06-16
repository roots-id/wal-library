package com.rootsid.wal.library.prism.model

import kotlinx.serialization.Serializable

@Serializable
data class DltDidUpdate(
    val operationId: String?,
    val did: Did?
)
