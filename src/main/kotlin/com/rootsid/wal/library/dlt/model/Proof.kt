package com.rootsid.wal.library.dlt.model

import kotlinx.serialization.Serializable

/**
 * Proof
 *
 * @property hash
 * @property index
 * @property siblings
 * @constructor Create empty Proof
 */
@Serializable
data class Proof(
    var hash: String,
    var index: Int,
    var siblings: MutableList<String> = mutableListOf()
)
