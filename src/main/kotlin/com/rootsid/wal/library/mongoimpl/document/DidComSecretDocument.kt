package com.rootsid.wal.library.mongoimpl.document

import com.rootsid.wal.library.didcom.model.DidComSecret
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class DidComSecretDocument(
    override val _id: String,
    override val secret: Map<String, @Contextual Any> = mutableMapOf()
) : DidComSecret
