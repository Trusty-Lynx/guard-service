package de.trustylynx.persistence

/** Eine Zeile aus devices. */
class Device(
    val deviceId: String,
    val userId: String,
    val publicKey: ByteArray,   // 65 Byte, unkomprimierter P-256-Punkt (04 ‖ x ‖ y)
    val status: String,         // "trusted" / "unknown" / "blocked"
)

interface DeviceRepository {
    /** null, wenn es die deviceId nicht gibt. */
    suspend fun find(deviceId: String): Device?

    /** true, wenn der User schon mindestens ein Gerät hat. */
    suspend fun hasDevices(userId: String): Boolean

    /** Wirft [de.trustylynx.common.AlreadyExists], wenn es die deviceId schon gibt. */
    suspend fun create(device: Device)
}
