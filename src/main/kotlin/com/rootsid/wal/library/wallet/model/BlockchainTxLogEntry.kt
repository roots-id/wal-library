package com.rootsid.wal.library.wallet.model

import kotlinx.serialization.Serializable

enum class BlockchainTxAction {
    ADD_KEY,
    REVOKE_KEY,
    PUBLISH_DID,
    ISSUE_CREDENTIAL,
    REVOKE_CREDENTIAL
}
