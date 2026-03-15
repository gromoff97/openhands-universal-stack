package adapter

import adapter.config.AdapterConfig
import adapter.converter.ChatCompletionToResponsesConverter
import adapter.converter.ResponsesToChatCompletionConverter
import adapter.http.corsFilter
import adapter.http.preflightResponse
import adapter.service.AdapterServiceFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    adapterApp().asServer(SunHttp(AdapterConfig.listenPort)).start()
    println("OpenHands ChatMock adapter listening on 0.0.0.0:${AdapterConfig.listenPort}")
}

private fun adapterApp(): HttpHandler {
    val service = AdapterServiceFactory.create(
        backendBaseUrl = AdapterConfig.backendBaseUrl,
        requestConverter = ResponsesToChatCompletionConverter.default(AdapterConfig.defaultModel),
        responseConverter = ChatCompletionToResponsesConverter.default(AdapterConfig.defaultModel),
    )

    val app = routes(
        "/v1/models" bind Method.GET to service::handleModels,
        "/v1/models" bind Method.OPTIONS to { preflightResponse() },
        "/v1/chat/completions" bind Method.POST to service::handleChatCompletions,
        "/v1/chat/completions" bind Method.OPTIONS to { preflightResponse() },
        "/v1/completions" bind Method.POST to service::handleCompletions,
        "/v1/completions" bind Method.OPTIONS to { preflightResponse() },
        "/v1/responses" bind Method.POST to service::handleResponses,
        "/v1/responses" bind Method.OPTIONS to { preflightResponse() },
    )

    return corsFilter().then(app)
}
