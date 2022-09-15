package com.rootsid.wal.library.didcomm.storage

import com.rootsid.wal.library.didcomm.model.DidCommSecret

interface DidCommSecretStorage {
    fun insert(kid: String, secretJson: Map<String, Any>): DidCommSecret

    fun findById(kid: String): DidCommSecret

    fun findIdsIn(kids: List<String>): Set<String>

    fun listIds(): List<String>

    fun list(): List<DidCommSecret>
}
