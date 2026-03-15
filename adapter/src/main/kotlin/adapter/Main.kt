package adapter

import adapter.config.adapterBackendBaseUrl
import adapter.config.adapterDefaultModel
import adapter.config.adapterListenPort
import adapter.converter.ChatCompletionResponseToResponsesApiResponseConverter
import adapter.converter.ResponsesRequestToChatCompletionRequestConverter
import adapter.converter.StreamingChatCompletionToResponsesSseConverter
import adapter.http.corsFilter
import adapter.model.ChatCompletionResponse
import adapter.model.ResponsesRequest
import adapter.route.RouteConfig
import adapter.route.routingConfig
import adapter.service.AdapterServiceFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.then
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    adapterApp().asServer(SunHttp(adapterListenPort)).start()
    println("OpenHands ChatMock adapter listening on 0.0.0.0:$adapterListenPort")
}

private fun adapterApp(): HttpHandler {
    val service = AdapterServiceFactory.create(adapterBackendBaseUrl)
    val routeConfigs = listOf<RouteConfig>(
        routingConfig("/v1/models", GET, "/v1/models"),
        routingConfig("/v1/chat/completions", POST, "/v1/chat/completions"),
        routingConfig("/v1/completions", POST, "/v1/completions"),
        routingConfig(
            publicPath = "/v1/responses",
            requestType = ResponsesRequest::class.java,
            requestConverter = ResponsesRequestToChatCompletionRequestConverter(adapterDefaultModel),
            backendPath = "/v1/chat/completions",
            backendResponseType = ChatCompletionResponse::class.java,
            responseConverter = ChatCompletionResponseToResponsesApiResponseConverter(adapterDefaultModel),
            streamingResponseConverter = StreamingChatCompletionToResponsesSseConverter(adapterDefaultModel),
        ),
    )
    val app = routes(*buildRoutes(service, routeConfigs).toTypedArray())

    return corsFilter().then(app)
}

private fun buildRoutes(service: adapter.service.AdapterService, routeConfigs: List<RouteConfig>) =
    routeConfigs.flatMap { routeConfig ->
        routeConfig.handlers(service)
    }
