package com.rootsid.wal.library.wallet.model

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
