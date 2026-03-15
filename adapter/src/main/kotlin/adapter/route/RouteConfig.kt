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

interface RoutingConfig {
    val sourcePath: String
    val destinationPath: String
    val method: Method
    val requestConverter: Converter<*, *>?
    val responseConverter: Converter<*, *>?
    val responseStreamConverter: Converter<StreamingBackendResponse, String>?

    fun handlers(service: AdapterService): List<RoutingHttpHandler>
}

fun routingConfig(
    sourcePath: String,
    method: Method,
    destinationPath: String,
): RoutingConfig = PassThroughRoutingConfig(
    sourcePath = sourcePath,
    method = method,
    destinationPath = destinationPath,
)

fun <OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any> routingConfig(
    sourcePath: String,
    requestType: Class<OriginalRequest>,
    requestConverter: Converter<OriginalRequest, AdaptedRequest>,
    destinationPath: String,
    backendResponseType: Class<BackendResponse>,
    responseConverter: Converter<BackendResponse, AdaptedResponse>,
    responseStreamConverter: Converter<StreamingBackendResponse, String>,
): RoutingConfig = AdaptedPostRoutingConfig(
    sourcePath = sourcePath,
    requestType = requestType,
    requestConverter = requestConverter,
    destinationPath = destinationPath,
    backendResponseType = backendResponseType,
    responseConverter = responseConverter,
    responseStreamConverter = responseStreamConverter,
)

private data class PassThroughRoutingConfig(
    override val sourcePath: String,
    override val destinationPath: String,
    override val method: Method,
    override val requestConverter: Converter<*, *>? = null,
    override val responseConverter: Converter<*, *>? = null,
    override val responseStreamConverter: Converter<StreamingBackendResponse, String>? = null,
) : RoutingConfig {
    override fun handlers(service: AdapterService): List<RoutingHttpHandler> = listOf(
        sourcePath bind method to { request ->
            service.handlePassThrough(request, destinationPath)
        },
        sourcePath bind OPTIONS to { preflightResponse() },
    )
}

private data class AdaptedPostRoutingConfig<OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any>(
    override val sourcePath: String,
    override val destinationPath: String,
    val requestType: Class<OriginalRequest>,
    override val requestConverter: Converter<OriginalRequest, AdaptedRequest>,
    val backendResponseType: Class<BackendResponse>,
    override val responseConverter: Converter<BackendResponse, AdaptedResponse>,
    override val responseStreamConverter: Converter<StreamingBackendResponse, String>,
) : RoutingConfig {
    override val method: Method = POST

    override fun handlers(service: AdapterService): List<RoutingHttpHandler> = listOf(
        sourcePath bind method to { request ->
            service.handleAdaptedPost(
                request = request,
                requestType = requestType,
                requestConverter = requestConverter,
                backendPath = destinationPath,
                backendResponseType = backendResponseType,
                responseConverter = responseConverter,
                streamingResponseConverter = responseStreamConverter,
            )
        },
        sourcePath bind OPTIONS to { preflightResponse() },
    )
}
