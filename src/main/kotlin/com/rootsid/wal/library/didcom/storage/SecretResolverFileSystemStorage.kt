package com.rootsid.wal.library.didcom.storage

//import com.rootsid.wal.library.didcom.model.createAnonymousObj
import com.rootsid.wal.library.didcom.model.DidComSecret
import kotlinx.serialization.Contextual
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.jwkToSecret
import org.didcommx.didcomm.secret.secretToJwk
import org.didcommx.didcomm.utils.fromJsonToList
import org.didcommx.didcomm.utils.toJson
import java.io.File
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.io.path.exists

class SecretResolverFileSystemStorage(private val filePath: String = "secrets.json") : DidComSecretStorage {
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

    override fun insert(kid: String, secretJson: Map<String, Any>): DidComSecret {
        secrets[kid] = jwkToSecret(secretJson)
        save()

        return createAnonymousDidComSecret(kid, secretToJwk(secrets[kid]!!))
    }

    private fun save() {
        val secretJson = toJson(secrets.values.map { secretToJwk(it) })
        File(filePath).writeText(secretJson)
    }

    private fun createAnonymousDidComSecret(id: String, secret: Map<String, @Contextual Any>): DidComSecret =
        object : DidComSecret {
            override val _id: String
                get() = id
            override val secret: Map<String, Any>
                get() = secret
        }

    override fun findById(kid: String): DidComSecret {
        secrets[kid]?.let {
            return createAnonymousDidComSecret(kid, secretToJwk(it))
        }

        throw RuntimeException("Secret '$kid' not found.")
    }

    override fun findIdsIn(kids: List<String>): Set<String> = kids.intersect(secrets.keys)

    override fun listIds(): List<String> = secrets.keys.toList()

    override fun list(): List<DidComSecret> = secrets.entries.map { createAnonymousDidComSecret(it.key, secretToJwk(it.value)) }
}
