package com.rootsid.wal.library.dlt.model

import kotlinx.serialization.Serializable

/**
 * Verified credential
 *
 * @property encodedSignedCredential
 * @property proof
 * @constructor Create empty Verified credential
 */
@Serializable
data class VerifiedCredential(
    val encodedSignedCredential: String,
    val proof: Proof
)
