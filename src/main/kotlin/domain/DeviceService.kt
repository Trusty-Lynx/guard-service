package de.trustylynx.domain

import de.trustylynx.common.AlreadyExists
import de.trustylynx.persistence.Device
import de.trustylynx.persistence.DeviceRepository
import de.trustylynx.common.NotFound


class DeviceService(
    private val devices: DeviceRepository
    ){
    suspend fun check(userId: String, deviceId: String): String {
        devices.find(userId, deviceId)?.let { return it.status }
        val status = if (devices.hasDevices(userId)) UNKNOWN else TRUSTED
        try {
            devices.create(Device(userId, deviceId, status))
        } catch (e: AlreadyExists) {
            // Zwei gleichzeitige Logins vom selben Browser: der andere war schneller → dessen Status nehmen
            return devices.find(userId, deviceId)?.status ?: throw e
        }
        // TODO: bei UNKNOWN Nachricht an alle trusted-Geräte des Users (Push-Kanal offen, #20)
        return status
    }
    // Nur lesen, nichts anlegen → unbekanntes Gerät = 404
    suspend fun status(userId: String, deviceId: String): String =
        devices.find(userId, deviceId)?.status ?: throw NotFound()

    companion object {
        const val TRUSTED = "trusted"
        const val UNKNOWN = "unknown"
        const val BLOCKED = "blocked"
    }
}