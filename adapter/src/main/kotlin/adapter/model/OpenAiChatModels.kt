package adapter.model

import com.fasterxml.jackson.databind.JsonNode

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    override val stream: Boolean = false,
    val tools: JsonNode? = null,
    val toolChoice: JsonNode? = null,
    val parallelToolCalls: Boolean? = null,
    val streamOptions: JsonNode? = null,
) : StreamAwareRequest

data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ChatToolCall>? = null,
)

data class ChatToolCall(
    val id: String? = null,
    val function: ChatFunction = ChatFunction(),
)

data class ChatFunction(
    val name: String? = null,
    val arguments: String? = null,
)

data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

data class ChatChoice(
    val message: ChatCompletionMessage? = null,
)

data class ChatCompletionMessage(
    val content: JsonNode? = null,
    val toolCalls: List<ChatToolCall>? = null,
)

data class ChatUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

data class ChatCompletionChunk(
    val choices: List<ChatChunkChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

data class ChatChunkChoice(
    val delta: ChatChunkDelta? = null,
)

data class ChatChunkDelta(
    val content: String? = null,
    val toolCalls: List<ChatToolCallDelta>? = null,
)

data class ChatToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val function: ChatFunctionDelta? = null,
)

data class ChatFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)
