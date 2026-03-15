package adapter.config

private fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Missing required environment variable: $name")

val adapterBackendBaseUrl: String = requiredEnv("ADAPTER_BACKEND_BASE_URL")
val adapterDefaultModel: String = requiredEnv("ADAPTER_DEFAULT_MODEL")
val adapterListenPort: Int = requiredEnv("ADAPTER_LISTEN_PORT").toIntOrNull()
    ?: error("ADAPTER_LISTEN_PORT must be an integer")
