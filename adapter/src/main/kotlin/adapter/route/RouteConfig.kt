package adapter.route

import adapter.converter.Converter
import adapter.http.StreamingBackendResponse
import adapter.http.preflightResponse
import adapter.model.StreamAwareRequest
import adapter.service.AdapterService
import org.http4k.core.Method
import org.http4k.core.Method.POST
import org.http4k.core.Method.OPTIONS
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

interface RouteConfig {
    val publicPath: String
    val method: Method

    fun handlers(service: AdapterService): List<RoutingHttpHandler>
}

fun routingConfig(
    publicPath: String,
    method: Method,
    backendPath: String,
): RouteConfig = PassThroughRoutingConfig(
    publicPath = publicPath,
    method = method,
    backendPath = backendPath,
)

fun <OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any> routingConfig(
    publicPath: String,
    requestType: Class<OriginalRequest>,
    requestConverter: Converter<OriginalRequest, AdaptedRequest>,
    backendPath: String,
    backendResponseType: Class<BackendResponse>,
    responseConverter: Converter<BackendResponse, AdaptedResponse>,
    streamingResponseConverter: Converter<StreamingBackendResponse, String>,
): RouteConfig = AdaptedPostRoutingConfig(
    publicPath = publicPath,
    requestType = requestType,
    requestConverter = requestConverter,
    backendPath = backendPath,
    backendResponseType = backendResponseType,
    responseConverter = responseConverter,
    streamingResponseConverter = streamingResponseConverter,
)

private data class PassThroughRoutingConfig(
    override val publicPath: String,
    override val method: Method,
    val backendPath: String,
) : RouteConfig {
    override fun handlers(service: AdapterService): List<RoutingHttpHandler> = listOf(
        publicPath bind method to { request ->
            service.handlePassThrough(request, backendPath)
        },
        publicPath bind OPTIONS to { preflightResponse() },
    )
}

private data class AdaptedPostRoutingConfig<OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any>(
    override val publicPath: String,
    val requestType: Class<OriginalRequest>,
    val requestConverter: Converter<OriginalRequest, AdaptedRequest>,
    val backendPath: String,
    val backendResponseType: Class<BackendResponse>,
    val responseConverter: Converter<BackendResponse, AdaptedResponse>,
    val streamingResponseConverter: Converter<StreamingBackendResponse, String>,
) : RouteConfig {
    override val method: Method = POST

    override fun handlers(service: AdapterService): List<RoutingHttpHandler> = listOf(
        publicPath bind method to { request ->
            service.handleAdaptedPost(
                request = request,
                requestType = requestType,
                requestConverter = requestConverter,
                backendPath = backendPath,
                backendResponseType = backendResponseType,
                responseConverter = responseConverter,
                streamingResponseConverter = streamingResponseConverter,
            )
        },
        publicPath bind OPTIONS to { preflightResponse() },
    )
}
