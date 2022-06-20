package com.rootsid.wal.library.dlt.model

import kotlinx.serialization.Serializable

/**
 * Dlt did update
 *
 * @property operationId operation id
 * @property did updated did
 * @constructor Create Dlt did update
 */
@Serializable
data class DltDidUpdate(
    val operationId: String?,
    val did: Did?
)
