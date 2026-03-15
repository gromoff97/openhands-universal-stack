package adapter.service

import adapter.converter.Converter
import adapter.http.BackendClient
import adapter.http.StreamingBackendResponse
import adapter.http.toHttp4kResponse
import adapter.json.adapterObjectMapper
import adapter.model.StreamAwareRequest
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class AdapterService(
    private val backendClient: BackendClient,
) {
    fun handlePassThrough(request: Request, backendPath: String): Response =
        backendClient.forward(request, backendPath).toHttp4kResponse()

    fun <OriginalRequest : Any, AdaptedRequest : StreamAwareRequest, BackendResponse : Any, AdaptedResponse : Any> handleAdaptedPost(
        request: Request,
        requestType: Class<OriginalRequest>,
        requestConverter: Converter<OriginalRequest, AdaptedRequest>,
        backendPath: String,
        backendResponseType: Class<BackendResponse>,
        responseConverter: Converter<BackendResponse, AdaptedResponse>,
        streamingResponseConverter: Converter<StreamingBackendResponse, String>,
    ): Response {
        val payload = try {
            adapterObjectMapper.readValue(request.bodyString(), requestType)
        } catch (_: Exception) {
            return jsonError(Status.BAD_REQUEST, "Invalid JSON body")
        }

        val chatRequest = try {
            requestConverter.adapt(payload)
        } catch (e: IllegalArgumentException) {
            return jsonError(Status.BAD_REQUEST, e.message ?: "Invalid request")
        }

        if (chatRequest.stream) {
            val upstream = backendClient.postJsonStreaming(
                request = request,
                path = backendPath,
                jsonBody = adapterObjectMapper.writeValueAsString(chatRequest),
            )

            if (upstream.statusCode >= 400) {
                upstream.use {
                    return it.upstream.use { response ->
                        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                        Response(Status(it.statusCode, ""))
                            .header("Content-Type", response.header("Content-Type") ?: "application/json")
                            .body(bodyBytes.inputStream(), bodyBytes.size.toLong())
                    }
                }
            }

            return Response(Status.OK)
                .header("Content-Type", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .body(streamingResponseConverter.adapt(upstream))
        }

        val upstream = backendClient.postJson(
            request = request,
            path = backendPath,
            jsonBody = adapterObjectMapper.writeValueAsString(chatRequest),
        )

        if (upstream.statusCode >= 400) {
            return upstream.toHttp4kResponse()
        }

        val chatCompletion = try {
            adapterObjectMapper.readValue(upstream.bodyBytes, backendResponseType)
        } catch (_: Exception) {
            return jsonError(Status.BAD_GATEWAY, "Upstream returned invalid JSON")
        }

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(adapterObjectMapper.writeValueAsString(responseConverter.adapt(chatCompletion)))
    }

    private fun jsonError(status: Status, message: String): Response =
        Response(status)
            .header("Content-Type", "application/json")
            .body("""{"error":{"message":${adapterObjectMapper.writeValueAsString(message)}}}""")
}
