package adapter.converter

import adapter.http.StreamingBackendResponse
import adapter.model.ChatCompletionChunk
import adapter.model.ChatCompletionMessage
import adapter.model.ChatCompletionResponse
import adapter.model.ChatToolCall
import adapter.model.ResponsesApiResponse
import adapter.model.ResponsesRequest
import adapter.model.ResponseCompletedEvent
import adapter.model.ResponseContentItem
import adapter.model.ResponseCreatedEvent
import adapter.model.ResponseOutputItem
import adapter.model.ResponseOutputTextDeltaEvent
import adapter.model.ResponseProgress
import adapter.model.ResponseUsage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class ChatCompletionToResponsesConverter(
    private val objectMapper: ObjectMapper,
    private val defaultModel: String,
) {
    fun convert(request: ResponsesRequest, chatCompletion: ChatCompletionResponse): ResponsesApiResponse {
        val model = request.model?.ifBlank { chatCompletion.model ?: defaultModel } ?: chatCompletion.model ?: defaultModel
        val createdAt = chatCompletion.created ?: Instant.now().epochSecond
        val message = chatCompletion.choices.firstOrNull()?.message
        val outputText = extractText(message)
        val output = buildOutputItems(outputText, message?.toolCalls.orEmpty())

        return ResponsesApiResponse(
            id = "resp_${UUID.randomUUID().toString().replace("-", "")}",
            createdAt = createdAt,
            status = "completed",
            model = model,
            output = output,
            outputText = outputText,
            usage = ResponseUsage(
                inputTokens = chatCompletion.usage?.promptTokens ?: 0,
                outputTokens = chatCompletion.usage?.completionTokens ?: 0,
                totalTokens = chatCompletion.usage?.totalTokens ?: 0,
            ),
        )
    }

    fun convertStream(request: ResponsesRequest, streaming: StreamingBackendResponse): String {
        streaming.use {
            val model = request.model?.ifBlank { defaultModel } ?: defaultModel
            val responseId = "resp_${UUID.randomUUID().toString().replace("-", "")}"
            val createdAt = Instant.now().epochSecond
            val fullText = StringBuilder()
            val toolCalls = linkedMapOf<Int, ToolCallAccumulator>()
            var usage = ResponseUsage(0, 0, 0)

            val events = StringBuilder()
            events.append(
                sse(
                    ResponseCreatedEvent(
                        response = ResponseProgress(
                            id = responseId,
                            createdAt = createdAt,
                            status = "in_progress",
                            model = model,
                        ),
                    ),
                ),
            )

            streaming.upstream.body?.charStream()?.buffered()?.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data: ")) return@forEach
                    val payload = line.removePrefix("data: ").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach

                    val chunk = try {
                        objectMapper.readValue(payload, ChatCompletionChunk::class.java)
                    } catch (_: Exception) {
                        return@forEach
                    }

                    chunk.usage?.let {
                        usage = ResponseUsage(
                            inputTokens = it.promptTokens ?: 0,
                            outputTokens = it.completionTokens ?: 0,
                            totalTokens = it.totalTokens ?: 0,
                        )
                    }

                    val delta = chunk.choices.firstOrNull()?.delta ?: return@forEach

                    delta.content?.takeIf { it.isNotEmpty() }?.let { piece ->
                        fullText.append(piece)
                        events.append(sse(ResponseOutputTextDeltaEvent(delta = piece)))
                    }

                    delta.toolCalls.orEmpty().forEach { toolCall ->
                        val index = toolCall.index ?: 0
                        val current = toolCalls.getOrPut(index) {
                            ToolCallAccumulator(
                                id = toolCall.id ?: "fc_${UUID.randomUUID().toString().take(24)}",
                                callId = toolCall.id ?: "call_${UUID.randomUUID().toString().take(24)}",
                            )
                        }
                        toolCall.function?.name?.takeIf { it.isNotEmpty() }?.let { current.name = it }
                        toolCall.function?.arguments?.let { current.arguments.append(it) }
                    }
                }
            }

            val output = buildOutputItems(
                outputText = fullText.toString(),
                toolCalls = toolCalls.values.map {
                    ChatToolCall(
                        id = it.id,
                        function = adapter.model.ChatFunction(
                            name = it.name,
                            arguments = it.arguments.toString(),
                        ),
                    )
                },
            )

            val response = ResponsesApiResponse(
                id = responseId,
                createdAt = createdAt,
                status = "completed",
                model = model,
                output = output,
                outputText = fullText.toString(),
                usage = usage,
            )
            events.append(sse(ResponseCompletedEvent(response = response)))
            events.append("data: [DONE]\n\n")
            return events.toString()
        }
    }

    private fun buildOutputItems(outputText: String, toolCalls: List<ChatToolCall>): List<ResponseOutputItem> {
        val items = mutableListOf<ResponseOutputItem>()

        if (outputText.isNotBlank()) {
            items += ResponseOutputItem(
                id = "msg_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                type = "message",
                role = "assistant",
                status = "completed",
                content = listOf(
                    ResponseContentItem(
                        type = "output_text",
                        text = outputText,
                    ),
                ),
            )
        }

        toolCalls.forEach { toolCall ->
            val callId = toolCall.id ?: "call_${UUID.randomUUID().toString().take(24)}"
            items += ResponseOutputItem(
                id = toolCall.id ?: "fc_${UUID.randomUUID().toString().take(24)}",
                type = "function_call",
                callId = callId,
                name = toolCall.function.name.orEmpty(),
                arguments = toolCall.function.arguments.orEmpty(),
                status = "completed",
            )
        }

        return items
    }

    private fun extractText(message: ChatCompletionMessage?): String {
        if (message == null) return ""
        val content = message.content ?: return ""
        if (content.isTextual) return content.asText()
        if (!content.isArray) return ""

        return content.mapNotNull { node ->
            when (node.path("type").asText()) {
                "input_text", "output_text", "text" -> node.path("text").takeIf(JsonNode::isTextual)?.asText()
                "refusal" -> node.path("refusal").takeIf(JsonNode::isTextual)?.asText()
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    private fun sse(payload: Any): String = "data: ${objectMapper.writeValueAsString(payload)}\n\n"

    private data class ToolCallAccumulator(
        val id: String,
        val callId: String,
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )
}
