package adapter.route

import adapter.converter.Converter
import adapter.http.StreamingBackendResponse
import adapter.http.preflightResponse
import adapter.model.StreamAwareRequest
import adapter.service.AdapterService
import org.http4k.core.Method
import org.http4k.core.Method.OPTIONS
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

sealed interface RouteConfig {
    val publicPath: String
    val method: Method

    fun bind(service: AdapterService): RoutingHttpHandler

    fun preflight(): RoutingHttpHandler = publicPath bind OPTIONS to { preflightResponse() }
}

data class PassThroughRouteConfig(
    override val publicPath: String,
    override val method: Method,
    val backendPath: String,
) : RouteConfig {
    override fun bind(service: AdapterService): RoutingHttpHandler =
        publicPath bind method to { request ->
            service.handlePassThrough(request, backendPath)
        }
}

data class AdaptedPostRouteConfig<OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any>(
    override val publicPath: String,
    val requestType: Class<OriginalRequest>,
    val requestConverter: Converter<OriginalRequest, AdaptedRequest>,
    val backendPath: String,
    val backendResponseType: Class<BackendResponse>,
    val responseConverter: Converter<BackendResponse, AdaptedResponse>,
    val streamingResponseConverter: Converter<StreamingBackendResponse, String>,
) : RouteConfig {
    override val method: Method = Method.POST

    override fun bind(service: AdapterService): RoutingHttpHandler =
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
        }
}
