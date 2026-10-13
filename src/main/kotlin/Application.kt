package de.trustylynx


import de.trustylynx.common.Secrets
import de.trustylynx.common.configureSerialization
import de.trustylynx.common.configureStatusPages
import de.trustylynx.common.createDataSource
import de.trustylynx.domain.deviceService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Einziger Ort, an dem Abhängigkeiten gebaut und verdrahtet werden. */
fun Application.module() {
    val config = AppConfig.fromEnv()

    val dataSource = createDataSource(config.db)
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    //val serverKeys = ServerKeyService(PostgresServerKeyRepository(dataSource), Secrets.msk)

    //configureApp(serverKeys)
}

/** Plugins und Routen, getrennt von [module], damit Tests Fakes einsetzen können. */
fun Application.configureApp(serverKeys: deviceService) {
    configureSerialization()
    configureStatusPages()
    routing {
        get("/test") { call.respondText("Hello, World!") }

    }
}
