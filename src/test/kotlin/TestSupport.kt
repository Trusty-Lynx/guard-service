package de.trustylynx

import de.trustylynx.common.AlreadyExists
import de.trustylynx.domain.DeviceService
import de.trustylynx.persistence.Device
import de.trustylynx.persistence.DeviceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap

// Testwert. Muss zu tasks.test in build.gradle.kts passen, weil Secrets ihn aus Umgebungsvariablen liest.
const val AUTH_TOKEN = "test-auth-token"

/** Startet die App ohne application.yaml (also ohne echte DB) mit einem In-Memory-Repository. */
fun withApp(
    repo: InMemoryDeviceRepository = InMemoryDeviceRepository(),
    block: suspend (HttpClient) -> Unit,
) = testApplication {
    // Leere Config, damit application.yaml nicht module() mit echter DB lädt.
    environment { config = MapApplicationConfig() }
    application { configureApp(DeviceService(repo)) }
    block(client)
}

suspend fun HttpClient.postJson(path: String, body: String, token: String? = null): HttpResponse = post(path) {
    contentType(ContentType.Application.Json)
    if (token != null) header("X-Service-Token", token)
    setBody(body)
}

suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

/** Fehlercode aus {"error": "..."} */
suspend fun HttpResponse.errorCode(): String = json()["error"]!!.jsonPrimitive.content

/** Erzeugt einen echten P-256-Schlüssel und gibt ihn roh (04 ‖ x ‖ y, 65 Byte) zurück, wie Web Crypto ihn exportiert. */
fun newPublicKey(): ByteArray {
    val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    val point = (generator.generateKeyPair().public as ECPublicKey).w
    // BigInteger kann ein führendes 0-Byte haben oder kürzer sein → auf genau 32 Byte bringen
    fun to32(n: java.math.BigInteger) = n.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
    return byteArrayOf(0x04) + to32(point.affineX) + to32(point.affineY)
}

/** base64url ohne Padding, so wie der Guard die deviceId speichert. */
fun b64url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** Neue gültige deviceId (= öffentlicher Schlüssel, base64url ohne Padding). */
fun newDeviceId(): String = b64url(newPublicKey())

/** Ersatz für PostgresDeviceRepository. Schlüssel ist wie in der DB (user_id, device_id). */
class InMemoryDeviceRepository : DeviceRepository {
    val rows = ConcurrentHashMap<Pair<String, String>, Device>()

    override suspend fun find(userId: String, deviceId: String): Device? = rows[userId to deviceId]

    override suspend fun hasDevices(userId: String): Boolean = rows.keys.any { it.first == userId }

    override suspend fun create(device: Device) {
        if (rows.putIfAbsent(device.userId to device.deviceId, device) != null) throw AlreadyExists()
    }

    /** Gerät direkt anlegen, z. B. um ein gesperrtes Gerät vorzubereiten. */
    fun put(userId: String, deviceId: String, status: String) {
        rows[userId to deviceId] = Device(userId = userId, deviceId = deviceId, status = status)
    }
}
