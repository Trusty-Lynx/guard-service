package de.trustylynx


import de.trustylynx.api.deviceCheckRoute
import de.trustylynx.api.getDeviceRoute
import de.trustylynx.common.configureSerialization
import de.trustylynx.common.configureStatusPages
import de.trustylynx.common.createDataSource
import de.trustylynx.domain.DeviceService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import de.trustylynx.persistence.PostgresDeviceRepository

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = AppConfig.fromEnv()

    val dataSource = createDataSource(config.db)
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    val devices = DeviceService(PostgresDeviceRepository(dataSource))

    configureApp(devices)
}

fun Application.configureApp(devices: DeviceService) {
    configureSerialization()
    configureStatusPages()
    routing {
        get("/test") { call.respondText("Hello, World!") }
        deviceCheckRoute(devices)
        getDeviceRoute(devices)
    }

}
