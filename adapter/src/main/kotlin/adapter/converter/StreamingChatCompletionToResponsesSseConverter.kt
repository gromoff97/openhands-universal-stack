package adapter.converter

import adapter.json.adapterObjectMapper
import adapter.model.ChatCompletionChunk
import adapter.model.ResponseCompletedEvent
import adapter.model.ResponseCreatedEvent
import adapter.model.ResponseOutputItem
import adapter.model.ResponseOutputTextDeltaEvent
import adapter.model.ResponseProgress
import adapter.model.ResponsesApiResponse
import adapter.model.ResponseContentItem
import adapter.model.ResponseUsage
import adapter.http.StreamingBackendResponse
import java.time.Instant
import java.util.UUID

class StreamingChatCompletionToResponsesSseConverter(
    private val defaultModel: String,
) : Converter<StreamingBackendResponse, String> {
    override fun adapt(original: StreamingBackendResponse): String {
        val streaming = original
        streaming.use {
            val model = defaultModel
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
                        adapterObjectMapper.readValue(payload, ChatCompletionChunk::class.java)
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

            val output = mutableListOf<ResponseOutputItem>()
            if (fullText.isNotEmpty()) {
                output += ResponseOutputItem(
                    id = "msg_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                    type = "message",
                    role = "assistant",
                    status = "completed",
                    content = listOf(
                        ResponseContentItem(
                            type = "output_text",
                            text = fullText.toString(),
                        ),
                    ),
                )
            }

            output += toolCalls.values.map {
                ResponseOutputItem(
                    id = it.id,
                    type = "function_call",
                    callId = it.callId,
                    name = it.name,
                    arguments = it.arguments.toString(),
                    status = "completed",
                )
            }

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

    private fun sse(payload: Any): String = "data: ${adapterObjectMapper.writeValueAsString(payload)}\n\n"

    private data class ToolCallAccumulator(
        val id: String,
        val callId: String,
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )
}
