package adapter.service

import adapter.converter.ResponsesRequestConverter
import adapter.converter.ResponsesResponseConverter
import adapter.http.ChatMockBackendClient
import adapter.json.adapterObjectMapper

object AdapterServiceFactory {
    fun create(
        backendBaseUrl: String,
        requestConverter: ResponsesRequestConverter,
        responseConverter: ResponsesResponseConverter,
    ): AdapterService =
        AdapterService(
            objectMapper = adapterObjectMapper(),
            backendClient = ChatMockBackendClient(backendBaseUrl),
            requestConverter = requestConverter,
            responseConverter = responseConverter,
        )
}
