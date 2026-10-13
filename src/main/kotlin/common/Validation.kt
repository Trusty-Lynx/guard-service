package de.trustylynx.common

import io.ktor.server.application.ApplicationCall
import java.security.MessageDigest
import kotlin.io.encoding.Base64

object Validate {

    // Gültig → purpose ("login" / "vault"), sonst Unauthorized → 401
    fun service(call: ApplicationCall): String {
        val token = call.request.headers["X-Service-Token"] ?: throw Unauthorized()
        return purposeForToken(token) ?: throw Unauthorized()
    }

    // Nur der Auth-Service darf durch.
    // Fehlt/unbekannt → 401, gültiges Token eines anderen Service (Vault, Crypto) → 403
    fun authService(call: ApplicationCall) {
        val token = call.request.headers["X-Service-Token"] ?: throw Unauthorized()
        val purpose = purposeForToken(token)
        when {
            purpose == "login" -> return
            purpose != null || algForToken(token) != null -> throw Forbidden()
            else -> throw Unauthorized()
        }
    }

    // Unbekannter purpose → 400, gehört zum anderen Service → 403
    fun purpose(tokenPurpose: String, requested: String) {
        if (requested != "login" && requested != "vault") throw BadRequest("unknown purpose")
        if (requested != tokenPurpose) throw Forbidden()
    }

    // Gültig → alg des Crypto-Service ("aes" / "chacha"), sonst Unauthorized → 401
    fun crypto(call: ApplicationCall): String {
        val token = call.request.headers["X-Service-Token"] ?: throw Unauthorized()
        return algForToken(token) ?: throw Unauthorized()
    }

    // Unbekannter alg → 400, gehört zum anderen Crypto-Service → 403
    fun alg(tokenAlg: String, requested: String) {
        if (requested != "aes" && requested != "chacha") throw BadRequest("unknown alg")
        if (requested != tokenAlg) throw Forbidden()
    }

    // Base64 → 32 Byte, sonst 400
    fun key(base64Key: String): ByteArray {
        val key = try { Base64.decode(base64Key) } catch (e: IllegalArgumentException) { throw BadRequest("key is not base64") }
        if (key.size != 32) throw BadRequest("key must be 32 bytes")
        return key
    }


    // Gibt zurück, für welchen purpose das Token gilt, oder null wenn es zu keinem passt
    private fun purposeForToken(token: String): String? {
        val t = token.toByteArray()
        // isEqual vergleicht in konstanter Zeit, damit man das Token nicht über Timing erraten kann
        return when {
            MessageDigest.isEqual(t, Secrets.authServiceToken.toByteArray()) -> "login"
            MessageDigest.isEqual(t, Secrets.vaultServiceToken.toByteArray()) -> "vault"
            else -> null
        }
    }

    // Crypto 1 darf nur K_aes, Crypto 2 nur K_chacha
    private fun algForToken(token: String): String? {
        val t = token.toByteArray()
        return when {
            MessageDigest.isEqual(t, Secrets.aesServiceToken.toByteArray()) -> "aes"
            MessageDigest.isEqual(t, Secrets.chachaServiceToken.toByteArray()) -> "chacha"
            else -> null
        }
    }
}
