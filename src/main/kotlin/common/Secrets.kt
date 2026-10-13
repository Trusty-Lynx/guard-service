package de.trustylynx.common

// Alle Secrets kommen aus Umgebungsvariablen (.env lokal / env_file im Container)
object Secrets {
    private fun envBytes32(name: String): ByteArray {
        val bytes = env(name).toByteArray()   // "7a11…" (64 Zeichen) → 32 Byte
        require(bytes.size == 32) { "$name muss 32 Byte haben" }
        return bytes
    }

    val authServiceToken = env("AUTH_SERVICE_TOKEN")
    val vaultServiceToken = env("VAULT_SERVICE_TOKEN")
    val aesServiceToken = env("AES_SERVICE_TOKEN")
    val chachaServiceToken = env("CHACHA_SERVICE_TOKEN")

    val pepperLogin: ByteArray = envBytes32("TEST_PEPPER_LOGIN")
    val pepperVault: ByteArray = envBytes32("TEST_PEPPER_VAULT")

    val msk: ByteArray = envBytes32("MSK")

}
