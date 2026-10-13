package de.trustylynx.api

import de.trustylynx.common.BadRequest
import kotlinx.serialization.Serializable
import io.ktor.server.routing.Route
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import de.trustylynx.common.Validate
import de.trustylynx.domain.deviceService
import kotlin.io.encoding.Base64

@Serializable
data class DeviceCheckRequest(
    val deviceId: String,
    val userId: String,
    val publicKey: String
)

@Serializable
data class DeviceCheckResponse(
    val status: String
)

fun Route.deviceCheck() {
    post("internal/device/check"){
        Validate.authService(call)
        val device = call.receive<DeviceCheckRequest>()
        val key = try{Base64.decode(device.publicKey)} catch(e: IllegalArgumentException){throw BadRequest("public key is not base64")}
        if (key.size != 65 || key[0] != 0x04.toByte()) throw BadRequest("publicKey must be 65 bytes starting with 04")
        val status = deviceService.check(device.userId, device.deviceId, key)
        respond(DeviceCheckResponse(status))
    }
}