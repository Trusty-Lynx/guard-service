package de.trustylynx

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

// GET /internal/get-devices?userId=...&deviceId=...
// Noch ohne Service-Token (TODO in GetDeviceRoute), deshalb keine 401-Tests
class GetDeviceRouteTest {

    // ---- Status lesen ----

    @Test
    fun `trusted device returns trusted`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        repo.put("u42", deviceId, "trusted")
        withApp(repo) { client ->
            val response = client.getDevice("u42", deviceId)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("trusted", response.deviceStatus())
        }
    }

    @Test
    fun `unknown device returns unknown`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        repo.put("u42", deviceId, "unknown")
        withApp(repo) { client ->
            val response = client.getDevice("u42", deviceId)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("unknown", response.deviceStatus())
        }
    }

    @Test
    fun `blocked device returns blocked`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        repo.put("u42", deviceId, "blocked")
        withApp(repo) { client ->
            val response = client.getDevice("u42", deviceId)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("blocked", response.deviceStatus())
        }
    }

    @Test
    fun `status from check is returned by get`() {
        val repo = InMemoryDeviceRepository()
        val deviceId = newDeviceId()
        withApp(repo) { client ->
            client.postJson("/internal/devices/check", """{"userId":"u42","deviceId":"$deviceId"}""", AUTH_TOKEN)

            val response = client.getDevice("u42", deviceId)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("trusted", response.deviceStatus())
        }
    }

    // ---- Nur lesen, nichts anlegen ----

    @Test
    fun `unknown device returns 404 and stores nothing`() {
        val repo = InMemoryDeviceRepository()
        withApp(repo) { client ->
            val response = client.getDevice("u42", newDeviceId())

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("not_found", response.errorCode())
            assertEquals(0, repo.rows.size)
        }
    }

    @Test
    fun `device of another user returns 404`() {
        val repo = InMemoryDeviceRepository()
        val sharedBrowser = newDeviceId()
        repo.put("u42", sharedBrowser, "trusted")
        withApp(repo) { client ->
            // Gleicher Browser, aber u77 hat ihn nie benutzt
            val response = client.getDevice("u77", sharedBrowser)

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `same browser has separate status per user`() {
        val repo = InMemoryDeviceRepository()
        val sharedBrowser = newDeviceId()
        repo.put("u42", sharedBrowser, "trusted")
        repo.put("u77", sharedBrowser, "blocked")
        withApp(repo) { client ->
            assertEquals("trusted", client.getDevice("u42", sharedBrowser).deviceStatus())
            assertEquals("blocked", client.getDevice("u77", sharedBrowser).deviceStatus())
        }
    }

    // ---- deviceId-Format ----

    @Test
    fun `deviceId with padding finds device stored without padding`() {
        val repo = InMemoryDeviceRepository()
        val key = newPublicKey()
        repo.put("u42", b64url(key), "trusted")
        withApp(repo) { client ->
            val withPadding = java.util.Base64.getUrlEncoder().encodeToString(key)   // endet auf "="

            val response = client.getDevice("u42", withPadding)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("trusted", response.deviceStatus())
        }
    }

    @Test
    fun `deviceId not base64url returns 400`() = withApp { client ->
        val response = client.getDevice("u42", "not base64!")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `deviceId with 64 bytes returns 400`() = withApp { client ->
        val response = client.getDevice("u42", b64url(newPublicKey().copyOf(64)))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `deviceId not starting with 04 returns 400`() = withApp { client ->
        val key = newPublicKey().also { it[0] = 0x02 }

        val response = client.getDevice("u42", b64url(key))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    // ---- Query-Parameter ----

    @Test
    fun `blank userId returns 400`() = withApp { client ->
        val response = client.getDevice("  ", newDeviceId())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `missing userId returns 400`() = withApp { client ->
        val response = client.getDevice(userId = null, deviceId = newDeviceId())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    @Test
    fun `missing deviceId returns 400`() = withApp { client ->
        val response = client.getDevice(userId = "u42", deviceId = null)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.errorCode())
    }

    // null = Parameter gar nicht mitschicken
    private suspend fun HttpClient.getDevice(userId: String?, deviceId: String?): HttpResponse = get(PATH) {
        if (userId != null) parameter("userId", userId)
        if (deviceId != null) parameter("deviceId", deviceId)
    }

    private suspend fun HttpResponse.deviceStatus(): String =
        json()["status"]!!.jsonPrimitive.content

    private companion object {
        const val PATH = "/internal/get-devices"
    }
}
