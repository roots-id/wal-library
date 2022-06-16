package com.rootsid.wal.library.prism.model

import kotlinx.serialization.Serializable

/**
 * Key pair
 *
 * @property keyId
 * @property didIdx
 * @property keyTypeValue
 * @property keyIdx
 * @property privateKey
 * @property publicKey
 * @property revoked
 * @constructor Create empty Key pair
 */
@Serializable
data class KeyPath(
    val keyId: String,
    val keyTypeValue: Int,
    val didIdx: Int,
    val keyDerivation: Int,
    val keyIdx: Int,
    var revoked: Boolean = false
)
