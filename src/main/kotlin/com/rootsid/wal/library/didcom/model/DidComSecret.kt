package com.rootsid.wal.library.didcom.model

import kotlinx.serialization.Contextual

interface DidComSecret {
    val _id: String
    val secret: Map<String, @Contextual Any>
}



