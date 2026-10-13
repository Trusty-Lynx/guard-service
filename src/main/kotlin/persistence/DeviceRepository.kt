package de.trustylynx.persistence

/** Eine Zeile aus devices. */
class Device(
    val userId: String,
    val deviceId: String,
    val status: String,         // "trusted" / "unknown" / "blocked"
)

interface DeviceRepository {
    /** null, wenn dieser User dieses Gerät noch nicht hat. */
    suspend fun find(userId: String, deviceId: String): Device?

    /** true, wenn der User schon mindestens ein Gerät hat. */
    suspend fun hasDevices(userId: String): Boolean

    /** Wirft [de.trustylynx.common.AlreadyExists], wenn es die deviceId schon gibt. */
    suspend fun create(device: Device)
}
