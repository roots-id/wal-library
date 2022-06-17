package com.rootsid.wal.library

import io.iohk.atala.prism.api.VerificationResult
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.credentials.json.JsonBasedCredential
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import io.iohk.atala.prism.protos.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

