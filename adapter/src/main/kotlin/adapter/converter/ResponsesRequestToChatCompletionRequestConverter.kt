package adapter.converter

import adapter.json.adapterObjectMapper
import adapter.model.ChatCompletionRequest
import adapter.model.ChatMessage
import adapter.model.LegacyMessage
import adapter.model.ResponsesFunctionCallOutput
import adapter.model.ResponsesInputMessage
import adapter.model.ResponsesRequest
import adapter.model.ResponsesTextPart
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

class ResponsesRequestToChatCompletionRequestConverter(
    private val defaultModel: String,
) : Converter<ResponsesRequest, ChatCompletionRequest> {
    override fun adapt(original: ResponsesRequest): ChatCompletionRequest {
        val messages = mutableListOf<ChatMessage>()

        original.instructions?.trim()?.takeIf { it.isNotEmpty() }?.let {
            messages += ChatMessage(role = "system", content = it)
        }

        messages += when {
            original.input == null && !original.messages.isNullOrEmpty() -> mapLegacyMessages(original.messages)
            original.input == null && !original.prompt.isNullOrBlank() -> listOf(ChatMessage(role = "user", content = original.prompt))
            original.input == null -> emptyList()
            original.input.isTextual -> listOf(ChatMessage(role = "user", content = original.input.asText()))
            original.input.isArray -> mapResponsesInput(original.input)
            else -> emptyList()
        }

        if (messages.isEmpty()) {
            throw IllegalArgumentException("Request must include input/messages/prompt")
        }

        return ChatCompletionRequest(
            model = original.model?.ifBlank { defaultModel } ?: defaultModel,
            messages = messages,
            stream = original.stream ?: false,
            tools = original.tools ?: original.responsesTools,
            toolChoice = original.toolChoice ?: original.responsesToolChoice,
            parallelToolCalls = original.parallelToolCalls,
            streamOptions = original.streamOptions,
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
            when (node.path("type").asText()) {
                "message" -> {
                    val item = adapterObjectMapper.treeToValue(node, ResponsesInputMessage::class.java)
                    val content = extractText(item.content)
                    if (content.isNotBlank()) {
                        messages += ChatMessage(
                            role = item.role?.ifBlank { "user" } ?: "user",
                            content = content,
                        )
                    }
                }

                "function_call_output" -> {
                    val item = adapterObjectMapper.treeToValue(node, ResponsesFunctionCallOutput::class.java)
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
            else -> adapterObjectMapper.writeValueAsString(output)
        }

    private fun extractText(content: JsonNode?): String {
        if (content == null || content.isNull) return ""
        if (content.isTextual) return content.asText()
        if (!content.isArray) return ""

        return content.mapNotNull { part ->
            val typedPart = adapterObjectMapper.treeToValue(part, ResponsesTextPart::class.java)
            when (typedPart.type) {
                "input_text", "output_text", "text" -> typedPart.text
                "refusal" -> typedPart.refusal
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n").trim()
    }
}
