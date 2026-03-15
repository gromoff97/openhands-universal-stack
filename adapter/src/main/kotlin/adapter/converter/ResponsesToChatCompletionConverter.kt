package adapter.converter

import adapter.model.ChatCompletionRequest
import adapter.model.ChatMessage
import adapter.model.LegacyMessage
import adapter.model.ResponsesFunctionCallOutput
import adapter.model.ResponsesInputMessage
import adapter.model.ResponsesRequest
import adapter.model.ResponsesTextPart
import adapter.json.adapterObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

class ResponsesToChatCompletionConverter(
    private val objectMapper: ObjectMapper,
    private val defaultModel: String,
) : ResponsesRequestConverter {
    companion object {
        fun default(defaultModel: String): ResponsesRequestConverter =
            ResponsesToChatCompletionConverter(
                objectMapper = adapterObjectMapper(),
                defaultModel = defaultModel,
            )
    }

    override fun convert(request: ResponsesRequest): ChatCompletionRequest {
        val messages = mutableListOf<ChatMessage>()

        request.instructions?.trim()?.takeIf { it.isNotEmpty() }?.let {
            messages += ChatMessage(role = "system", content = it)
        }

        messages += when {
            request.input == null && !request.messages.isNullOrEmpty() -> mapLegacyMessages(request.messages)
            request.input == null && !request.prompt.isNullOrBlank() -> listOf(ChatMessage(role = "user", content = request.prompt))
            request.input == null -> emptyList()
            request.input.isTextual -> listOf(ChatMessage(role = "user", content = request.input.asText()))
            request.input.isArray -> mapResponsesInput(request.input)
            else -> emptyList()
        }

        if (messages.isEmpty()) {
            throw IllegalArgumentException("Request must include input/messages/prompt")
        }

        return ChatCompletionRequest(
            model = request.model?.ifBlank { defaultModel } ?: defaultModel,
            messages = messages,
            stream = request.stream ?: false,
            tools = request.tools ?: request.responsesTools,
            toolChoice = request.toolChoice ?: request.responsesToolChoice,
            parallelToolCalls = request.parallelToolCalls,
            streamOptions = request.streamOptions,
        )
    }

    private fun mapLegacyMessages(messages: List<LegacyMessage>): List<ChatMessage> =
        messages.mapNotNull { item ->
            val role = item.role?.ifBlank { "user" } ?: "user"
            val content = extractText(item.content)
            if (content.isBlank()) null else ChatMessage(role = role, content = content)
        }

    private fun mapResponsesInput(input: JsonNode): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        input.forEach { node ->
            val type = node.path("type").asText()
            when (type) {
                "message" -> {
                    val item = objectMapper.treeToValue(node, ResponsesInputMessage::class.java)
                    val content = extractText(item.content)
                    if (content.isNotBlank()) {
                        messages += ChatMessage(
                            role = item.role?.ifBlank { "user" } ?: "user",
                            content = content,
                        )
                    }
                }

                "function_call_output" -> {
                    val item = objectMapper.treeToValue(node, ResponsesFunctionCallOutput::class.java)
                    messages += ChatMessage(
                        role = "tool",
                        content = normalizeOutput(item.output),
                        toolCallId = item.callId ?: item.id ?: "tool_${UUID.randomUUID().toString().take(8)}",
                    )
                }
            }
        }
        return messages
    }

    private fun normalizeOutput(output: JsonNode?): String =
        when {
            output == null || output.isNull -> ""
            output.isTextual -> output.asText()
            else -> objectMapper.writeValueAsString(output)
        }

    private fun extractText(content: JsonNode?): String {
        if (content == null || content.isNull) return ""
        if (content.isTextual) return content.asText()
        if (!content.isArray) return ""

        return content.mapNotNull { part ->
            val typedPart = objectMapper.treeToValue(part, ResponsesTextPart::class.java)
            when (typedPart.type) {
                "input_text", "output_text", "text" -> typedPart.text
                "refusal" -> typedPart.refusal
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n").trim()
    }
}
