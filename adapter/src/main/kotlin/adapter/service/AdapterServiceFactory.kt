package adapter.service

import adapter.http.ChatMockBackendClient

object AdapterServiceFactory {
    fun create(backendBaseUrl: String): AdapterService = AdapterService(
        backendClient = ChatMockBackendClient(backendBaseUrl),
    )
}
