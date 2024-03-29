package com.rootsid.wal.library.didcomm.model

import kotlinx.serialization.Contextual
import java.io.Serializable

/**
 * Didcomm secret
 * @property _id - id
 * @property secret - secret map
 */
interface DidCommSecret : Serializable {
    val _id: String
    val secret: Map<String, @Contextual Any>
}
