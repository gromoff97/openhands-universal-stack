package adapter.service

import adapter.converter.ResponsesRequestConverter
import adapter.converter.ResponsesResponseConverter
import adapter.http.BackendClient
import adapter.http.toHttp4kResponse
import adapter.model.ChatCompletionResponse
import adapter.model.ResponsesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class AdapterService(
    private val objectMapper: ObjectMapper,
    private val backendClient: BackendClient,
    private val requestConverter: ResponsesRequestConverter,
    private val responseConverter: ResponsesResponseConverter,
) {
    fun handleModels(request: Request): Response =
        backendClient.forward(request, "/v1/models").toHttp4kResponse()

    fun handleChatCompletions(request: Request): Response =
        backendClient.forward(request, "/v1/chat/completions").toHttp4kResponse()

    fun handleCompletions(request: Request): Response =
        backendClient.forward(request, "/v1/completions").toHttp4kResponse()

    fun handleResponses(request: Request): Response {
        val payload = try {
            objectMapper.readValue(request.bodyString(), ResponsesRequest::class.java)
        } catch (_: Exception) {
            return jsonError(Status.BAD_REQUEST, "Invalid JSON body")
        }

        val chatRequest = try {
            requestConverter.convert(payload)
        } catch (e: IllegalArgumentException) {
            return jsonError(Status.BAD_REQUEST, e.message ?: "Invalid request")
        }

        if (chatRequest.stream) {
            val upstream = backendClient.postJsonStreaming(
                request = request,
                path = "/v1/chat/completions",
                jsonBody = objectMapper.writeValueAsString(chatRequest),
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
                .body(responseConverter.convertStream(payload, upstream))
        }

        val upstream = backendClient.postJson(
            request = request,
            path = "/v1/chat/completions",
            jsonBody = objectMapper.writeValueAsString(chatRequest),
        )

        if (upstream.statusCode >= 400) {
            return upstream.toHttp4kResponse()
        }

        val chatCompletion = try {
            objectMapper.readValue(upstream.bodyBytes, ChatCompletionResponse::class.java)
        } catch (_: Exception) {
            return jsonError(Status.BAD_GATEWAY, "Upstream returned invalid JSON")
        }

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(responseConverter.convert(payload, chatCompletion)))
    }

    private fun jsonError(status: Status, message: String): Response =
        Response(status)
            .header("Content-Type", "application/json")
            .body("""{"error":{"message":${objectMapper.writeValueAsString(message)}}}""")
}
