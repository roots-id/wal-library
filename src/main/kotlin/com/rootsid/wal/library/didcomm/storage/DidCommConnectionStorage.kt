package com.rootsid.wal.library.didcomm.storage

import com.rootsid.wal.library.didcomm.model.DidCommConnection

interface DidCommConnectionStorage {
    fun insert(conn: DidCommConnection): DidCommConnection

    fun findById(id: String): DidCommConnection

    fun list(): List<DidCommConnection>
}
