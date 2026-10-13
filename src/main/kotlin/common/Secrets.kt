package de.trustylynx.common

// Alle Secrets kommen aus Umgebungsvariablen (.env lokal / env_file im Container)
object Secrets {
    val authServiceToken = env("AUTH_SERVICE_TOKEN")
}
