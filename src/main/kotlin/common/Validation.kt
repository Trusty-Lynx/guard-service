package de.trustylynx.common

import io.ktor.server.application.ApplicationCall
import java.security.MessageDigest

object Validate {

    // Nur der Auth-Service darf durch. Fehlt/falsch → 401
    fun authService(call: ApplicationCall) {
        val token = call.request.headers["X-Service-Token"] ?: throw Unauthorized()
        // isEqual vergleicht in konstanter Zeit, damit man das Token nicht über Timing erraten kann
        if (!MessageDigest.isEqual(token.toByteArray(), Secrets.authServiceToken.toByteArray())) throw Unauthorized()
    }

}
