package com.rootsid.wal.library.didcom.storage

import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolverEditable
import org.didcommx.didcomm.secret.jwkToSecret
import org.didcommx.didcomm.secret.secretToJwk
import org.didcommx.didcomm.utils.fromJsonToList
import org.didcommx.didcomm.utils.toJson
import java.io.File
import java.util.Optional
import kotlin.io.path.Path
import kotlin.io.path.exists

class SecretResolver(private val filePath: String = "secrets.json") : SecretResolverEditable {

    private val secrets: MutableMap<String, Secret>

    init {
        if (!Path(filePath).exists()) {
            secrets = mutableMapOf()
            save()
        } else {
            val secretsJson = File(filePath).readText()
            secrets = if (secretsJson.isNotEmpty()) {
                fromJsonToList(secretsJson).map { jwkToSecret(it) }.associate { it.kid to it }.toMutableMap()
            } else {
                mutableMapOf()
            }
        }
    }

    private fun save() {
        val secretJson = toJson(secrets.values.map { secretToJwk(it) })
        File(filePath).writeText(secretJson)
    }

    override fun addKey(secret: Secret) {
        secrets.put(secret.kid, secret)
        save()
    }

    override fun getKids(): List<String> =
        secrets.keys.toList()

    override fun findKey(kid: String): Optional<Secret> =
        Optional.ofNullable(secrets.get(kid))

    override fun findKeys(kids: List<String>): Set<String> =
        kids.intersect(secrets.keys)
}
