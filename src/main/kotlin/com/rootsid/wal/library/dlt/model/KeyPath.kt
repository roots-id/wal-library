package com.rootsid.wal.library.dlt.model

import kotlinx.serialization.Serializable

/**
 * Key path for a key derivation function.
 *
 * @property keyId - key identifier.
 * @property keyTypeValue - key type value.
 * @property didIdx - DID index.
 * @property keyIdx - key index.
 * @property revoked
 * @constructor Create empty Key pair
 */
@Serializable
data class KeyPath(
    val keyId: String,
    val keyTypeValue: Int,
    val didIdx: Int,
    @Deprecated("Use keyIdx instead")
    val keyDerivation: Int,
    val keyIdx: Int,
    var revoked: Boolean = false
)
