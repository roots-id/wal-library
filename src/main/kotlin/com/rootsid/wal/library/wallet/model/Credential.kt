package com.rootsid.wal.library.wallet.model

import com.rootsid.wal.library.dlt.model.VerifiedCredential

interface Credential {
    val alias: String
    var verifiedCredential: VerifiedCredential
}
