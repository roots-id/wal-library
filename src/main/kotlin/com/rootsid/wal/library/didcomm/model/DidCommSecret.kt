package com.rootsid.wal.library.didcomm.model

import kotlinx.serialization.Contextual
import java.io.Serializable

interface DidCommSecret: Serializable {
    val _id: String
    val secret: Map<String, @Contextual Any>
}



