package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.didcomm.model.DidCommSecret
import kotlinx.serialization.Contextual

data class DidCommSecretDocument(
    override val _id: String,
    override val secret: Map<String, @Contextual Any> = mutableMapOf()
) : DidCommSecret
