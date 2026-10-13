package de.trustylynx.api

import de.trustylynx.common.BadRequest
import de.trustylynx.common.Validate
import de.trustylynx.domain.DeviceService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
data class DeviceCheckRequest(
    val userId: String,
    val deviceId: String,   // öffentlicher Schlüssel des Browsers, base64url
)

@Serializable
data class DeviceCheckResponse(
    val status: String,
)

// base64url, Padding (=) optional, damit "abc" und "abc=" gleich behandelt werden
private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

fun Route.deviceCheckRoute(devices: DeviceService) {
    post("/internal/devices/check") {
        // 1. Token prüfen, noch bevor der Body gelesen wird → 401/403
        Validate.authService(call)

        // Kaputtes JSON / fehlendes Feld → 400 über StatusPages
        val request = call.receive<DeviceCheckRequest>()
        if (request.userId.isBlank()) throw BadRequest("userId missing")

        // 2. deviceId muss ein unkomprimierter P-256-Schlüssel sein: 65 Byte, beginnt mit 04
        val key = try { base64Url.decode(request.deviceId) } catch (e: IllegalArgumentException) { throw BadRequest("deviceId is not base64url") }
        if (key.size != 65 || key[0] != 0x04.toByte()) throw BadRequest("deviceId must be 65 bytes starting with 04")

        // 3. Einheitlich (ohne Padding) speichern, sonst wäre derselbe Schlüssel zweimal in der DB
        val deviceId = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(key)

        val status = devices.check(request.userId, deviceId)
        call.respond(DeviceCheckResponse(status))
    }
}
