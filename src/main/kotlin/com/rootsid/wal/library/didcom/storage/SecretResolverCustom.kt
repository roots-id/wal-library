package com.rootsid.wal.library.didcom.storage

import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolverEditable
import org.didcommx.didcomm.secret.jwkToSecret
import org.didcommx.didcomm.secret.secretToJwk
import java.util.*

class SecretResolverCustom(private val didComSecretStorage: DidComSecretStorage = SecretResolverFileSystemStorage()) :
    SecretResolverEditable {

    override fun addKey(secret: Secret) {
        didComSecretStorage.insert(secret.kid, secretToJwk(secret))
    }

    override fun findKey(kid: String): Optional<Secret> =
        Optional.of(jwkToSecret(didComSecretStorage.findById(kid).secret))

    override fun getKids(): List<String> = didComSecretStorage.listIds()

    override fun findKeys(kids: List<String>): Set<String> = didComSecretStorage.findIdsIn(kids)
}
