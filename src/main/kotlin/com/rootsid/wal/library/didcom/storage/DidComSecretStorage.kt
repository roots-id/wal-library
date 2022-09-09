package com.rootsid.wal.library.didcom.storage

import com.rootsid.wal.library.didcom.model.DidComSecret

interface DidComSecretStorage {
    fun insert(kid: String, secretJson: Map<String, Any>): DidComSecret

    fun findById(kid: String): DidComSecret

    fun findIdsIn(kids: List<String>): Set<String>

    fun listIds(): List<String>

    fun list(): List<DidComSecret>
}
