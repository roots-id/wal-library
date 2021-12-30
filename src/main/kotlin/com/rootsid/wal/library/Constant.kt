package com.rootsid.wal.library

import io.iohk.atala.prism.protos.GrpcOptions

/**
 * Constant
 *
 * @constructor Create empty Constant
 */
object Constant {
    const val MNEMONIC_SEPARATOR = ","
    const val TESTNET_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction?id="
}

// TODO: get values from environment or config file
class EnvVar {
    companion object {
        val grpcOptions = GrpcOptions("https", "ppp.atalaprism.io", 50053)
    }
}
