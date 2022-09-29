package com.rootsid.wal.library.didcomm.model

/**
 * Unpack result
 *
 * @property message
 * @property from - DID of sender
 * @property to - DID of recipient
 * @property res - unpack result
 * @constructor Create empty Unpack result
 */
data class UnpackResult(
    val message: String,
    val from: String?,
    val to: String,
    val res: org.didcommx.didcomm.model.UnpackResult
)
