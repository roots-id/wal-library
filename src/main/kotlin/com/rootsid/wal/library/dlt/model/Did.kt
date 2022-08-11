package com.rootsid.wal.library.dlt.model

import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.models.AtalaOperationStatusEnum
import kotlinx.serialization.Serializable

/**
 * D i d
 *
 * @property alias
 * @property didIdx
 * @property uriCanonical
 * @property uriLongForm
 * @property operationHash
 * @property keyPaths
 * @constructor Create empty D i d
 */
@Serializable
data class Did(
    val alias: String,
    val didIdx: Int,
    val uriCanonical: String,
    val uriLongForm: String,
    var keyPaths: MutableList<KeyPath> = mutableListOf(),
    var operationHash: String = "",
    var publishedStatus: AtalaOperationStatusEnum = AtalaOperationStatus.UNKNOWN_OPERATION,
    var publishedOperationId: String = ""
)
