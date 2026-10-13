package de.trustylynx.persistence

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.withConnection
import java.sql.SQLException
import javax.sql.DataSource

class PostgresDeviceRepository(private val dataSource: DataSource) : DeviceRepository {

    override suspend fun find(userId: String, deviceId: String): Device? =
        dataSource.withConnection { connection ->
            connection.prepareStatement(
                "SELECT status FROM devices WHERE user_id = ? AND device_id = ?",
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, deviceId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) Device(userId, deviceId, rs.getString("status")) else null
                }
            }
        }

    override suspend fun hasDevices(userId: String): Boolean =
        dataSource.withConnection { connection ->
            // LIMIT 1: uns interessiert nur, ob es überhaupt eins gibt
            connection.prepareStatement("SELECT 1 FROM devices WHERE user_id = ? LIMIT 1").use { statement ->
                statement.setString(1, userId)
                statement.executeQuery().use { rs -> rs.next() }
            }
        }

    override suspend fun create(device: Device) {
        dataSource.withConnection { connection ->
            try {
                connection.prepareStatement(
                    "INSERT INTO devices (user_id, device_id, status) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, device.userId)
                    statement.setString(2, device.deviceId)
                    statement.setString(3, device.status)
                    statement.executeUpdate()
                }
            } catch (e: SQLException) {
                // Primary Key (user_id, device_id) verletzt → Gerät gibt es schon
                if (e.sqlState == UNIQUE_VIOLATION) throw AlreadyExists()
                throw e
            }
        }
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
