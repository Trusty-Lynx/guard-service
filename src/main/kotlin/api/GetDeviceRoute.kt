package de.trustylynx.api

import de.trustylynx.common.BadRequest
import de.trustylynx.domain.DeviceService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
data class GetDeviceRequest(
    val deviceId: String,
    val userId: String
)

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

fun Route.getDeviceRoute(devices: DeviceService) {
    get("/internal/get-devices") {
        //TODO einbauen dass das Gateway ein Token hat oder antwort so gestalten dass nur zurück kommt ob ja oder nein
        // Pfad-Parameter: fehlen können sie bei diesem Pfad eigentlich nicht, aber leer sein
        val userId = call.queryParameters["userId"]?.takeIf { it.isNotBlank() } ?: throw BadRequest("userId missing")
        val rawDeviceId = call.queryParameters["deviceId"] ?: throw BadRequest("deviceId missing")

        val key = try { base64Url.decode(rawDeviceId) } catch (e: IllegalArgumentException) { throw BadRequest("deviceId is not base64url") }
        if (key.size != 65 || key[0] != 0x04.toByte()) throw BadRequest("deviceId must be 65 bytes starting with 04")
        // Gleich normalisieren wie bei check, sonst findet der Lookup Keys mit Padding nicht
        val deviceId = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(key)

        call.respond(DeviceCheckResponse(devices.status(userId, deviceId)))
    }
}
