package adapter.converter

import adapter.model.ChatCompletionMessage
import adapter.model.ChatCompletionResponse
import adapter.model.ChatToolCall
import adapter.model.ResponsesApiResponse
import adapter.model.ResponseContentItem
import adapter.model.ResponseOutputItem
import adapter.model.ResponseUsage
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

class ChatCompletionResponseToResponsesApiResponseConverter(
    private val defaultModel: String,
) : Converter<ChatCompletionResponse, ResponsesApiResponse> {
    override fun adapt(original: ChatCompletionResponse): ResponsesApiResponse {
        val chatCompletion = original
        val model = chatCompletion.model?.ifBlank { defaultModel } ?: defaultModel
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
}
