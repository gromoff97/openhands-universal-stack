package adapter.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResponsesRequest(
    @JsonProperty("model") val model: String? = null,
    @JsonProperty("instructions") val instructions: String? = null,
    @JsonProperty("input") val input: JsonNode? = null,
    @JsonProperty("messages") val messages: List<LegacyMessage>? = null,
    @JsonProperty("prompt") val prompt: String? = null,
    @JsonProperty("tools") val tools: JsonNode? = null,
    @JsonProperty("tool_choice") val toolChoice: JsonNode? = null,
    @JsonProperty("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    @JsonProperty("responses_tools") val responsesTools: JsonNode? = null,
    @JsonProperty("responses_tool_choice") val responsesToolChoice: JsonNode? = null,
    @JsonProperty("stream") val stream: Boolean? = null,
    @JsonProperty("stream_options") val streamOptions: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacyMessage(
    @JsonProperty("role") val role: String? = null,
    @JsonProperty("content") val content: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResponsesInputMessage(
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("role") val role: String? = null,
    @JsonProperty("content") val content: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResponsesFunctionCallOutput(
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("call_id") val callId: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("output") val output: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResponsesTextPart(
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("text") val text: String? = null,
    @JsonProperty("refusal") val refusal: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResponsesApiResponse(
    @JsonProperty("id") val id: String,
    @JsonProperty("object") val objectType: String = "response",
    @JsonProperty("created_at") val createdAt: Long,
    @JsonProperty("status") val status: String,
    @JsonProperty("model") val model: String,
    @JsonProperty("output") val output: List<ResponseOutputItem>,
    @JsonProperty("output_text") val outputText: String,
    @JsonProperty("usage") val usage: ResponseUsage,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResponseOutputItem(
    @JsonProperty("id") val id: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("role") val role: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("content") val content: List<ResponseContentItem>? = null,
    @JsonProperty("call_id") val callId: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("arguments") val arguments: String? = null,
)

data class ResponseContentItem(
    @JsonProperty("type") val type: String,
    @JsonProperty("text") val text: String,
    @JsonProperty("annotations") val annotations: List<Any> = emptyList(),
)

data class ResponseUsage(
    @JsonProperty("input_tokens") val inputTokens: Int,
    @JsonProperty("output_tokens") val outputTokens: Int,
    @JsonProperty("total_tokens") val totalTokens: Int,
)

data class ResponseCreatedEvent(
    @JsonProperty("type") val type: String = "response.created",
    @JsonProperty("response") val response: ResponseProgress,
)

data class ResponseProgress(
    @JsonProperty("id") val id: String,
    @JsonProperty("object") val objectType: String = "response",
    @JsonProperty("created_at") val createdAt: Long,
    @JsonProperty("status") val status: String,
    @JsonProperty("model") val model: String,
)

data class ResponseOutputTextDeltaEvent(
    @JsonProperty("type") val type: String = "response.output_text.delta",
    @JsonProperty("delta") val delta: String,
    @JsonProperty("output_index") val outputIndex: Int = 0,
    @JsonProperty("content_index") val contentIndex: Int = 0,
)

data class ResponseCompletedEvent(
    @JsonProperty("type") val type: String = "response.completed",
    @JsonProperty("response") val response: ResponsesApiResponse,
)
