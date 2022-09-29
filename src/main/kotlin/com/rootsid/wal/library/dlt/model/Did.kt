package com.rootsid.wal.library.dlt.model

import kotlinx.serialization.Serializable

/**
 * Did
 *
 * @property alias - alias of the DID
 * @property didIdx - The index of the DID in the wallet. For deterministic DID creation
 * @property uriCanonical - canonical uri, For public DIDs, this is the DID URI.
 * @property uriLongForm - long form of the DID. For non-published DIDs
 * @property keyPaths - A list of key paths for the DID
 * @property operationId - Array of operation ids that have been performed on this DID.
 * @property operationHash - Array of operation hashes that have been performed on this DID.

 * @constructor Create empty D i d
 */
@Serializable
data class Did(
    val alias: String,
    val didIdx: Int,
    val uriCanonical: String,
    val uriLongForm: String,
    var keyPaths: MutableList<KeyPath> = mutableListOf(),
    var operationId: MutableList<String> = mutableListOf(),
    var operationHash: MutableList<String> = mutableListOf()
)
