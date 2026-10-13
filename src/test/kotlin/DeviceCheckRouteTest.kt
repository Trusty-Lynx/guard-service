package de.trustylynx

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// POST /internal/devices/check, documentation/api/guard-service/check-device.md
class DeviceCheckRouteTest {

    // ---- Fälle aus der Doku ----

    @Test
    fun `first device of a user is stored as trusted`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        withApp(repo) { client ->
            val response = client.postJson(PATH, body("u42", deviceId), AUTH_TOKEN)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("trusted", response.deviceStatus())
            // Prüft auch, dass userId und deviceId nicht vertauscht gespeichert werden
            val stored = assertNotNull(repo.rows["u42" to deviceId])
            assertEquals("u42", stored.userId)
            assertEquals(deviceId, stored.deviceId)
            assertEquals("trusted", stored.status)
        }
    }

    @Test
    fun `known device returns its stored status and is not stored twice`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        withApp(repo) { client ->
            client.postJson(PATH, body("u42", deviceId), AUTH_TOKEN)
            val response = client.postJson(PATH, body("u42", deviceId), AUTH_TOKEN)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("trusted", response.deviceStatus())
            assertEquals(1, repo.rows.size)
        }
    }

    @Test
    fun `further device of a user is stored as unknown`() {
        val repo = InMemoryDeviceRepository()
        val laptop = newDeviceId()
        val phone = newDeviceId()
        withApp(repo) { client ->
            client.postJson(PATH, body("u42", laptop), AUTH_TOKEN)
            val response = client.postJson(PATH, body("u42", phone), AUTH_TOKEN)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("unknown", response.deviceStatus())
            assertEquals("unknown", repo.rows["u42" to phone]!!.status)
        }
    }

    @Test
    fun `unknown device stays unknown on next login`() {
        val repo = InMemoryDeviceRepository()
        val laptop = newDeviceId()
        val phone = newDeviceId()
        withApp(repo) { client ->
            client.postJson(PATH, body("u42", laptop), AUTH_TOKEN)
            client.postJson(PATH, body("u42", phone), AUTH_TOKEN)
            val response = client.postJson(PATH, body("u42", phone), AUTH_TOKEN)

            assertEquals("unknown", response.deviceStatus())
        }
    }

    @Test
    fun `blocked device returns blocked`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        repo.put("u42", deviceId, "blocked")
        withApp(repo) { client ->
            val response = client.postJson(PATH, body("u42", deviceId), AUTH_TOKEN)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("blocked", response.deviceStatus())
        }
    }

    // ---- Ein Browser, mehrere Konten ----

    @Test
    fun `same browser for another user gets its own row and status`() {
        val repo = InMemoryDeviceRepository()
        val sharedBrowser = newDeviceId()
        withApp(repo) { client ->
            // u77 hat schon ein anderes Gerät, für u77 ist der Browser also neu
            client.postJson(PATH, body("u77", newDeviceId()), AUTH_TOKEN)
            client.postJson(PATH, body("u42", sharedBrowser), AUTH_TOKEN)

            val response = client.postJson(PATH, body("u77", sharedBrowser), AUTH_TOKEN)

            assertEquals("unknown", response.deviceStatus())
            assertEquals("trusted", repo.rows["u42" to sharedBrowser]!!.status)
            assertEquals("unknown", repo.rows["u77" to sharedBrowser]!!.status)
        }
    }

    @Test
    fun `blocked for one user does not block the same browser for another user`() {
        val repo = InMemoryDeviceRepository()
        val sharedBrowser = newDeviceId()
        repo.put("u42", sharedBrowser, "blocked")
        withApp(repo) { client ->
            val response = client.postJson(PATH, body("u77", sharedBrowser), AUTH_TOKEN)

            assertEquals("trusted", response.deviceStatus())
        }
    }

    // ---- deviceId-Format ----

    @Test
    fun `deviceId with padding is stored without padding`() {
        val repo = InMemoryDeviceRepository()
        val key = newPublicKey()
        val withPadding = java.util.Base64.getUrlEncoder().encodeToString(key)   // endet auf "="
        withApp(repo) { client ->
            val response = client.postJson(PATH, body("u42", withPadding), AUTH_TOKEN)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(setOf("u42" to b64url(key)), repo.rows.keys)
        }
    }

    @Test
    fun `same key with and without padding is the same device`() {
        val repo = InMemoryDeviceRepository()
        val key = newPublicKey()
        withApp(repo) { client ->
            client.postJson(PATH, body("u42", b64url(key)), AUTH_TOKEN)
            client.postJson(PATH, body("u42", java.util.Base64.getUrlEncoder().encodeToString(key)), AUTH_TOKEN)

            assertEquals(1, repo.rows.size)
        }
    }

    @Test
    fun `deviceId not base64url returns 400`() = withApp { client ->
        val response = client.postJson(PATH, body("u42", "not base64!"), AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `deviceId with 64 bytes returns 400`() = withApp { client ->
        val response = client.postJson(PATH, body("u42", b64url(newPublicKey().copyOf(64))), AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `deviceId not starting with 04 returns 400`() = withApp { client ->
        val key = newPublicKey().also { it[0] = 0x02 }   // komprimierter Punkt wäre 33 Byte, hier nur falsches Präfix

        val response = client.postJson(PATH, body("u42", b64url(key)), AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    // ---- Request-Format ----

    @Test
    fun `blank userId returns 400`() = withApp { client ->
        val response = client.postJson(PATH, body("  ", newDeviceId()), AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `missing field returns 400`() = withApp { client ->
        val response = client.postJson(PATH, """{"userId":"u42"}""", AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `malformed json returns 400`() = withApp { client ->
        val response = client.postJson(PATH, """{"userId":""", AUTH_TOKEN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    // ---- Service-Token ----

    @Test
    fun `missing token returns 401 and stores nothing`() {
        val repo = InMemoryDeviceRepository()
        withApp(repo) { client ->
            val response = client.postJson(PATH, body("u42", newDeviceId()), token = null)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("unauthorized", response.errorCode())
            assertEquals(0, repo.rows.size)
        }
    }

    @Test
    fun `unknown token returns 401`() = withApp { client ->
        val response = client.postJson(PATH, body("u42", newDeviceId()), "wrong-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("unauthorized", response.errorCode())
    }

    private fun body(userId: String, deviceId: String) =
        """{"userId":"$userId","deviceId":"$deviceId"}"""

    private suspend fun io.ktor.client.statement.HttpResponse.deviceStatus(): String =
        json()["status"]!!.jsonPrimitive.content

    private companion object {
        const val PATH = "/internal/devices/check"
    }
}
