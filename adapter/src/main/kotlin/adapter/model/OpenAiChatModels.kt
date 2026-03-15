package adapter.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("messages") val messages: List<ChatMessage>,
    @JsonProperty("stream") val stream: Boolean = false,
    @JsonProperty("tools") val tools: JsonNode? = null,
    @JsonProperty("tool_choice") val toolChoice: JsonNode? = null,
    @JsonProperty("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    @JsonProperty("stream_options") val streamOptions: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("tool_call_id") val toolCallId: String? = null,
    @JsonProperty("tool_calls") val toolCalls: List<ChatToolCall>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatToolCall(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("function") val function: ChatFunction = ChatFunction(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatFunction(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("arguments") val arguments: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionResponse(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("model") val model: String? = null,
    @JsonProperty("created") val created: Long? = null,
    @JsonProperty("choices") val choices: List<ChatChoice> = emptyList(),
    @JsonProperty("usage") val usage: ChatUsage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatChoice(
    @JsonProperty("message") val message: ChatCompletionMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionMessage(
    @JsonProperty("content") val content: JsonNode? = null,
    @JsonProperty("tool_calls") val toolCalls: List<ChatToolCall>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatUsage(
    @JsonProperty("prompt_tokens") val promptTokens: Int? = null,
    @JsonProperty("completion_tokens") val completionTokens: Int? = null,
    @JsonProperty("total_tokens") val totalTokens: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionChunk(
    @JsonProperty("choices") val choices: List<ChatChunkChoice> = emptyList(),
    @JsonProperty("usage") val usage: ChatUsage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatChunkChoice(
    @JsonProperty("delta") val delta: ChatChunkDelta? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatChunkDelta(
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("tool_calls") val toolCalls: List<ChatToolCallDelta>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatToolCallDelta(
    @JsonProperty("index") val index: Int? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("function") val function: ChatFunctionDelta? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatFunctionDelta(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("arguments") val arguments: String? = null,
)
