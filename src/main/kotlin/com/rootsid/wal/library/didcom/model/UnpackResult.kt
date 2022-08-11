package com.rootsid.wal.library.didcom.model

/**
 * Unpack result
 *
 * @property message
 * @property from
 * @property to
 * @property res
 * @constructor Create empty Unpack result
 */
data class UnpackResult(
    val message: String,
    val from: String?,
    val to: String,
    val res: org.didcommx.didcomm.model.UnpackResult
)
