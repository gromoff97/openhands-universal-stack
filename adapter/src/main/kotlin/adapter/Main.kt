package adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val BACKEND_BASE = "http://chatmock:8000"
private const val DEFAULT_MODEL = "openai/gpt-5.1-codex-max"
private const val LISTEN_PORT = 5000

private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
private val jsonMediaType = "application/json".toMediaTypeOrNull()!!
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(30))
    .readTimeout(Duration.ofSeconds(600))
    .writeTimeout(Duration.ofSeconds(600))
    .build()

private val hopByHopHeaders = setOf(
    "connection",
    "content-length",
    "host",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "content-encoding",
)

fun main() {
    adapterApp().asServer(SunHttp(LISTEN_PORT)).start()
    println("OpenHands ChatMock adapter listening on 0.0.0.0:$LISTEN_PORT")
}

private fun adapterApp(): HttpHandler {
    val app = routes(
        "/v1/models" bind Method.GET to { request -> proxyRequest(request, "/v1/models") },
        "/v1/models" bind Method.OPTIONS to { preflight() },
        "/v1/chat/completions" bind Method.POST to { request -> proxyRequest(request, "/v1/chat/completions") },
        "/v1/chat/completions" bind Method.OPTIONS to { preflight() },
        "/v1/completions" bind Method.POST to { request -> proxyRequest(request, "/v1/completions") },
        "/v1/completions" bind Method.OPTIONS to { preflight() },
        "/v1/responses" bind Method.POST to ::responses,
        "/v1/responses" bind Method.OPTIONS to { preflight() },
    )

    return corsFilter(app)
}

private val corsFilter = Filter { next ->
    { request ->
        applyCors(next(request), request)
    }
}

private fun preflight(): Response = Response(Status.NO_CONTENT)

private fun responses(request: Request): Response {
    val rawBody = request.bodyString()
    val payload = try {
        if (rawBody.isBlank()) mapper.createObjectNode() else mapper.readTree(rawBody)
    } catch (_: Exception) {
        return jsonError(Status.BAD_REQUEST, "Invalid JSON body")
    }

    val payloadObject = payload as? ObjectNode ?: return jsonError(Status.BAD_REQUEST, "Invalid JSON body")
    val chatRequest = try {
        buildChatRequest(payloadObject)
    } catch (e: IllegalArgumentException) {
        return jsonError(Status.BAD_REQUEST, e.message ?: "Invalid request")
    }

    val upstream = executeBackendRequest(
        request = request,
        path = "/v1/chat/completions",
        method = Method.POST,
        body = mapper.writeValueAsString(chatRequest),
    )

    upstream.use { response ->
        if (response.code >= 400) {
            val responseBody = response.body?.string().orEmpty()
            return if (responseBody.isNotBlank()) {
                Response(Status(response.code, ""))
                    .header("Content-Type", response.header("Content-Type") ?: "application/json")
                    .body(responseBody)
            } else {
                jsonError(Status(response.code, ""), "Upstream error")
            }
        }

        if (chatRequest.path("stream").asBoolean(false)) {
            val sseBody = convertStreamingChatCompletionToResponses(payloadObject, response)
            return Response(Status.OK)
                .header("Content-Type", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .body(sseBody)
        }

        val upstreamBody = response.body?.string().orEmpty()
        val chatCompletion = try {
            mapper.readTree(upstreamBody)
        } catch (_: Exception) {
            return jsonError(Status.BAD_GATEWAY, "Upstream returned invalid JSON")
        }

        val responsePayload = convertChatCompletionToResponses(payloadObject, chatCompletion)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(responsePayload))
    }
}

private fun proxyRequest(request: Request, path: String): Response {
    val body = when (request.method) {
        Method.GET, Method.OPTIONS -> null
        else -> request.bodyString()
    }

    val upstream = executeBackendRequest(request, path, request.method, body)

    upstream.use { response ->
        val responseBytes = response.body?.bytes() ?: ByteArray(0)
        var proxied = Response(Status(response.code, ""))
            .body(responseBytes.inputStream(), responseBytes.size.toLong())

        response.headers.forEach { header ->
            if (header.first.lowercase() !in hopByHopHeaders) {
                proxied = proxied.header(header.first, header.second)
            }
        }

        return proxied
    }
}

private fun executeBackendRequest(
    request: Request,
    path: String,
    method: Method,
    body: String?,
): okhttp3.Response {
    val url = buildBackendUrl(path, request.uri.toString())
    val builder = okhttp3.Request.Builder().url(url)
    copyForwardHeaders(request, builder)

    when (method) {
        Method.GET -> builder.get()
        Method.POST -> {
            val contentType = request.header("Content-Type")?.toMediaTypeOrNull() ?: jsonMediaType
            builder.post((body ?: "").toRequestBody(contentType))
        }
        Method.OPTIONS -> builder.method("OPTIONS", null)
        else -> error("Unsupported method: $method")
    }

    return httpClient.newCall(builder.build()).execute()
}

private fun buildBackendUrl(path: String, uri: String): String {
    val query = uri.substringAfter('?', "")
    return if (query.isBlank()) "$BACKEND_BASE$path" else "$BACKEND_BASE$path?$query"
}

private fun copyForwardHeaders(request: Request, builder: okhttp3.Request.Builder) {
    request.headers.forEach { (name, value) ->
        if (value != null && name.lowercase() !in hopByHopHeaders) {
            builder.addHeader(name, value)
        }
    }
}

private fun applyCors(response: Response, request: Request): Response {
    val origin = request.header("Origin") ?: "*"
    return response
        .header("Access-Control-Allow-Origin", origin)
        .header(
            "Access-Control-Allow-Headers",
            "Authorization, Content-Type, X-Requested-With, x-stainless-lang, x-stainless-package-version, x-stainless-os, x-stainless-arch, x-stainless-runtime, x-stainless-runtime-version, OpenAI-Beta",
        )
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        .header("Access-Control-Allow-Credentials", "true")
        .header("Vary", "Origin")
}

private fun buildChatRequest(payload: ObjectNode): ObjectNode {
    val messages = responsesInputToMessages(payload)
    if (messages.isEmpty) {
        throw IllegalArgumentException("Request must include input/messages/prompt")
    }

    val body = mapper.createObjectNode()
    body.put("model", payload.path("model").asText(DEFAULT_MODEL).ifBlank { DEFAULT_MODEL })
    body.set<ArrayNode>("messages", messages)
    body.put("stream", payload.path("stream").asBoolean(false))

    copyIfPresent(payload, body, "tools")
    copyIfPresent(payload, body, "tool_choice")
    copyIfPresent(payload, body, "parallel_tool_calls")
    copyIfPresent(payload, body, "stream_options")

    if (!body.has("tools") && payload.has("responses_tools")) {
        body.set<JsonNode>("tools", payload.get("responses_tools"))
    }
    if (!body.has("tool_choice") && payload.has("responses_tool_choice")) {
        body.set<JsonNode>("tool_choice", payload.get("responses_tool_choice"))
    }

    return body
}

private fun responsesInputToMessages(payload: ObjectNode): ArrayNode {
    val messages = mapper.createArrayNode()

    val instructions = payload.path("instructions").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
    if (instructions.isNotEmpty()) {
        messages.add(messageNode("system", instructions))
    }

    val inputNode = payload.get("input")
    when {
        inputNode == null && payload.path("messages").isArray -> {
            payload.path("messages").forEach { item ->
                if (!item.isObject) return@forEach
                val role = item.path("role").asText("user")
                val content = extractTextParts(item.get("content"))
                if (content.isNotBlank()) {
                    messages.add(messageNode(role, content))
                }
            }
            return messages
        }

        inputNode == null && payload.path("prompt").isTextual -> {
            val prompt = payload.path("prompt").asText()
            if (prompt.isNotBlank()) {
                messages.add(messageNode("user", prompt))
            }
            return messages
        }

        inputNode?.isTextual == true -> {
            messages.add(messageNode("user", inputNode.asText()))
        }

        inputNode?.isArray == true -> {
            inputNode.forEach { item ->
                if (!item.isObject) return@forEach
                when (item.path("type").asText()) {
                    "message" -> {
                        val role = item.path("role").asText("user")
                        val text = extractTextParts(item.get("content"))
                        if (text.isNotBlank()) {
                            messages.add(messageNode(role, text))
                        }
                    }

                    "function_call_output" -> {
                        val output = when (val node = item.get("output")) {
                            null -> ""
                            else -> if (node.isTextual) node.asText() else mapper.writeValueAsString(node)
                        }
                        val toolMessage = messageNode("tool", output)
                        toolMessage.put(
                            "tool_call_id",
                            item.path("call_id").asText(
                                item.path("id").asText("tool_${UUID.randomUUID().toString().take(8)}"),
                            ),
                        )
                        messages.add(toolMessage)
                    }
                }
            }
        }
    }

    return messages
}

private fun convertChatCompletionToResponses(originalRequest: ObjectNode, chatCompletion: JsonNode): ObjectNode {
    val requestedModel = originalRequest.path("model").asText().ifBlank {
        chatCompletion.path("model").asText(DEFAULT_MODEL)
    }
    val createdAt = chatCompletion.path("created").takeIf { it.isNumber }?.asLong() ?: Instant.now().epochSecond
    val responseId = "resp_${UUID.randomUUID().toString().replace("-", "")}"

    val response = mapper.createObjectNode()
    response.put("id", responseId)
    response.put("object", "response")
    response.put("created_at", createdAt)
    response.put("status", "completed")
    response.put("model", requestedModel)

    val output = mapper.createArrayNode()
    val message = firstArrayElement(chatCompletion.path("choices"))?.path("message")
    val (text, toolCalls) = messageTextAndToolCalls(message)

    if (text.isNotBlank()) {
        val contentItems = mapper.createArrayNode()
        val textNode = mapper.createObjectNode()
        textNode.put("type", "output_text")
        textNode.put("text", text)
        textNode.set<ArrayNode>("annotations", mapper.createArrayNode())
        contentItems.add(textNode)

        val assistantMessage = mapper.createObjectNode()
        assistantMessage.put("id", "msg_${UUID.randomUUID().toString().replace("-", "").take(24)}")
        assistantMessage.put("type", "message")
        assistantMessage.put("role", "assistant")
        assistantMessage.put("status", "completed")
        assistantMessage.set<ArrayNode>("content", contentItems)
        output.add(assistantMessage)
    }

    toolCalls.forEach { toolCall ->
        val function = toolCall.path("function")
        val callId = toolCall.path("id").asText("call_${UUID.randomUUID().toString().take(24)}")
        val functionCall = mapper.createObjectNode()
        functionCall.put("id", toolCall.path("id").asText("fc_${UUID.randomUUID().toString().take(24)}"))
        functionCall.put("type", "function_call")
        functionCall.put("call_id", callId)
        functionCall.put("name", function.path("name").asText(""))
        functionCall.put("arguments", function.path("arguments").asText(""))
        functionCall.put("status", "completed")
        output.add(functionCall)
    }

    response.set<ArrayNode>("output", output)
    response.put("output_text", text)

    val usage = mapper.createObjectNode()
    usage.put("input_tokens", chatCompletion.path("usage").path("prompt_tokens").asInt(0))
    usage.put("output_tokens", chatCompletion.path("usage").path("completion_tokens").asInt(0))
    usage.put("total_tokens", chatCompletion.path("usage").path("total_tokens").asInt(0))
    response.set<ObjectNode>("usage", usage)

    return response
}

private fun convertStreamingChatCompletionToResponses(
    originalRequest: ObjectNode,
    upstream: okhttp3.Response,
): String {
    val requestedModel = originalRequest.path("model").asText(DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
    val responseId = "resp_${UUID.randomUUID().toString().replace("-", "")}"
    val createdAt = Instant.now().epochSecond
    val fullText = StringBuilder()
    val toolCalls = linkedMapOf<Int, ObjectNode>()
    var usage = mapper.createObjectNode().apply {
        put("input_tokens", 0)
        put("output_tokens", 0)
        put("total_tokens", 0)
    }

    val events = StringBuilder()
    events.append(
        sse(
            mapper.createObjectNode().apply {
                put("type", "response.created")
                set<ObjectNode>(
                    "response",
                    mapper.createObjectNode().apply {
                        put("id", responseId)
                        put("object", "response")
                        put("created_at", createdAt)
                        put("status", "in_progress")
                        put("model", requestedModel)
                    },
                )
            },
        ),
    )

    upstream.body?.charStream()?.buffered()?.useLines { lines ->
        lines.forEach { line ->
            if (!line.startsWith("data: ")) return@forEach
            val data = line.removePrefix("data: ").trim()
            if (data.isBlank()) return@forEach
            if (data == "[DONE]") return@forEach

            val event = try {
                mapper.readTree(data)
            } catch (_: Exception) {
                return@forEach
            }

            if (event.path("usage").isObject) {
                usage = mapper.createObjectNode().apply {
                    put("input_tokens", event.path("usage").path("prompt_tokens").asInt(0))
                    put("output_tokens", event.path("usage").path("completion_tokens").asInt(0))
                    put("total_tokens", event.path("usage").path("total_tokens").asInt(0))
                }
            }

            val delta = firstArrayElement(event.path("choices"))?.path("delta") ?: return@forEach

            val contentPiece = delta.path("content")
            if (contentPiece.isTextual && contentPiece.asText().isNotEmpty()) {
                val piece = contentPiece.asText()
                fullText.append(piece)
                events.append(
                    sse(
                        mapper.createObjectNode().apply {
                            put("type", "response.output_text.delta")
                            put("delta", piece)
                            put("output_index", 0)
                            put("content_index", 0)
                        },
                    ),
                )
            }

            if (delta.path("tool_calls").isArray) {
                delta.path("tool_calls").forEach { toolCall ->
                    if (!toolCall.isObject) return@forEach
                    val index = toolCall.path("index").asInt(0)
                    val state = toolCalls.getOrPut(index) {
                        mapper.createObjectNode().apply {
                            put("id", toolCall.path("id").asText("fc_${UUID.randomUUID().toString().take(24)}"))
                            put("call_id", toolCall.path("id").asText("call_${UUID.randomUUID().toString().take(24)}"))
                            put("name", "")
                            put("arguments", "")
                        }
                    }
                    val function = toolCall.path("function")
                    if (function.path("name").isTextual && function.path("name").asText().isNotEmpty()) {
                        state.put("name", function.path("name").asText())
                    }
                    if (function.path("arguments").isTextual) {
                        state.put("arguments", state.path("arguments").asText("") + function.path("arguments").asText())
                    }
                }
            }
        }
    }

    val output = mapper.createArrayNode()
    if (fullText.isNotEmpty()) {
        output.add(
            mapper.createObjectNode().apply {
                put("id", "msg_${UUID.randomUUID().toString().take(24)}")
                put("type", "message")
                put("role", "assistant")
                put("status", "completed")
                set<ArrayNode>(
                    "content",
                    mapper.createArrayNode().apply {
                        add(
                            mapper.createObjectNode().apply {
                                put("type", "output_text")
                                put("text", fullText.toString())
                                set<ArrayNode>("annotations", mapper.createArrayNode())
                            },
                        )
                    },
                )
            },
        )
    }

    toolCalls.values.forEach { state ->
        output.add(
            mapper.createObjectNode().apply {
                put("id", state.path("id").asText())
                put("type", "function_call")
                put("call_id", state.path("call_id").asText())
                put("name", state.path("name").asText())
                put("arguments", state.path("arguments").asText())
                put("status", "completed")
            },
        )
    }

    events.append(
        sse(
            mapper.createObjectNode().apply {
                put("type", "response.completed")
                set<ObjectNode>(
                    "response",
                    mapper.createObjectNode().apply {
                        put("id", responseId)
                        put("object", "response")
                        put("created_at", createdAt)
                        put("status", "completed")
                        put("model", requestedModel)
                        set<ArrayNode>("output", output)
                        put("output_text", fullText.toString())
                        set<ObjectNode>("usage", usage)
                    },
                )
            },
        ),
    )
    events.append("data: [DONE]\n\n")
    return events.toString()
}

private fun copyIfPresent(from: ObjectNode, to: ObjectNode, key: String) {
    val value = from.get(key)
    if (value != null && !value.isNull) {
        to.set<JsonNode>(key, value)
    }
}

private fun messageNode(role: String, content: String): ObjectNode =
    mapper.createObjectNode().apply {
        put("role", role)
        put("content", content)
    }

private fun extractTextParts(content: JsonNode?): String {
    if (content == null || content.isNull) return ""
    if (content.isTextual) return content.asText()
    if (!content.isArray) return ""

    val parts = mutableListOf<String>()
    content.forEach { item ->
        if (!item.isObject) return@forEach
        when (item.path("type").asText()) {
            "input_text", "output_text", "text" -> if (item.path("text").isTextual) parts += item.path("text").asText()
            "refusal" -> if (item.path("refusal").isTextual) parts += item.path("refusal").asText()
        }
    }
    return parts.filter { it.isNotBlank() }.joinToString("\n").trim()
}

private fun messageTextAndToolCalls(message: JsonNode?): Pair<String, List<JsonNode>> {
    if (message == null || message.isMissingNode || message.isNull) return "" to emptyList()
    val text = when {
        message.path("content").isTextual -> message.path("content").asText()
        else -> extractTextParts(message.get("content"))
    }
    val toolCalls = if (message.path("tool_calls").isArray) {
        message.path("tool_calls").toList()
    } else {
        emptyList()
    }
    return text to toolCalls
}

private fun firstArrayElement(node: JsonNode?): JsonNode? =
    if (node != null && node.isArray && node.size() > 0) node[0] else null

private fun jsonError(status: Status, message: String): Response =
    Response(status)
        .header("Content-Type", "application/json")
        .body(
            mapper.writeValueAsString(
                mapper.createObjectNode().apply {
                    set<ObjectNode>(
                        "error",
                        mapper.createObjectNode().apply {
                            put("message", message)
                        },
                    )
                },
            ),
        )

private fun sse(payload: JsonNode): String = "data: ${mapper.writeValueAsString(payload)}\n\n"
