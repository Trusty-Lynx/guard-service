package de.trustylynx.domain

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.NotFound
import de.trustylynx.persistence.Device
import de.trustylynx.persistence.DeviceRepository
import java.security.PublicKey
import java.sql.Array
import kotlin.io.encoding.Base64

class DeviceService(
    private val devices: DeviceRepository
    ){
    suspend fun check(userId: String, deviceId: String, publicKey: ByteArray?): String{
        val existing = devices.find(deviceId)
        if(existing != null){
            if(existing.userId != userId) throw AlreadyExists()

            if(publicKey != null && !publicKey.contentEquals(existing.publicKey)) throw AlreadyExists()
            return existing.status
        }
        if (publicKey == null) throw NotFound()
        val status = if (devices.hasDevices(userId)) UNKNOWN else TRUSTED
        devices.create(Device(deviceId, userId, publicKey, status))
        // TODO: bei UNKNOWN Nachricht an alle trusted-Geräte (Push-Kanal offen, #20)
        return status
    }
    companion object{
        const val TRUSTED = "trusted"
        const val UNKNOWN = "unknown"
        const val BLOCKED = "blocked"
    }
}