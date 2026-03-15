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
import adapter.route.RoutingConfig
import adapter.route.routingConfig
import adapter.service.AdapterServiceFactory
import adapter.service.AdapterService
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
    val routeConfigs = listOf<RoutingConfig>(
        routingConfig(sourcePath = "/v1/models", method = GET, destinationPath = "/v1/models"),
        routingConfig(sourcePath = "/v1/chat/completions", method = POST, destinationPath = "/v1/chat/completions"),
        routingConfig(sourcePath = "/v1/completions", method = POST, destinationPath = "/v1/completions"),
        routingConfig(
            sourcePath = "/v1/responses",
            requestType = ResponsesRequest::class.java,
            requestConverter = ResponsesRequestToChatCompletionRequestConverter(adapterDefaultModel),
            destinationPath = "/v1/chat/completions",
            backendResponseType = ChatCompletionResponse::class.java,
            responseConverter = ChatCompletionResponseToResponsesApiResponseConverter(adapterDefaultModel),
            responseStreamConverter = StreamingChatCompletionToResponsesSseConverter(adapterDefaultModel),
        ),
    )
    val app = routes(*buildRoutes(service, routeConfigs).toTypedArray())

    return corsFilter().then(app)
}

private fun buildRoutes(service: AdapterService, routeConfigs: List<RoutingConfig>) =
    routeConfigs.flatMap { routeConfig ->
        routeConfig.handlers(service)
    }
