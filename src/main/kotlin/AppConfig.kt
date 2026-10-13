package de.trustylynx

import de.trustylynx.common.DbConfig
import de.trustylynx.common.envOrDev

class AppConfig(
    val db: DbConfig,
) {
    companion object {
        fun fromEnv() = AppConfig(
            db = DbConfig(
                url = envOrDev("DB_URL", "jdbc:postgresql://localhost:5434/guard"),
                user = envOrDev("DB_USER", "dev"),
                password = envOrDev("DB_PASSWORD", "dev"),
                poolName = "guard-db",
            )
        )
    }
}