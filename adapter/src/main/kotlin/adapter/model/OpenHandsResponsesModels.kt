package adapter.model

import com.fasterxml.jackson.databind.JsonNode

data class ResponsesRequest(
    val model: String? = null,
    val instructions: String? = null,
    val input: JsonNode? = null,
    val messages: List<LegacyMessage>? = null,
    val prompt: String? = null,
    val tools: JsonNode? = null,
    val toolChoice: JsonNode? = null,
    val parallelToolCalls: Boolean? = null,
    val responsesTools: JsonNode? = null,
    val responsesToolChoice: JsonNode? = null,
    val stream: Boolean? = null,
    val streamOptions: JsonNode? = null,
)

data class LegacyMessage(
    val role: String? = null,
    val content: JsonNode? = null,
)

data class ResponsesInputMessage(
    val type: String? = null,
    val role: String? = null,
    val content: JsonNode? = null,
)

data class ResponsesFunctionCallOutput(
    val type: String? = null,
    val callId: String? = null,
    val id: String? = null,
    val output: JsonNode? = null,
)

data class ResponsesTextPart(
    val type: String? = null,
    val text: String? = null,
    val refusal: String? = null,
)

data class ResponsesApiResponse(
    val id: String,
    val `object`: String = "response",
    val createdAt: Long,
    val status: String,
    val model: String,
    val output: List<ResponseOutputItem>,
    val outputText: String,
    val usage: ResponseUsage,
)

data class ResponseOutputItem(
    val id: String,
    val type: String,
    val role: String? = null,
    val status: String? = null,
    val content: List<ResponseContentItem>? = null,
    val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

data class ResponseContentItem(
    val type: String,
    val text: String,
    val annotations: List<Any> = emptyList(),
)

data class ResponseUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class ResponseCreatedEvent(
    val type: String = "response.created",
    val response: ResponseProgress,
)

data class ResponseProgress(
    val id: String,
    val `object`: String = "response",
    val createdAt: Long,
    val status: String,
    val model: String,
)

data class ResponseOutputTextDeltaEvent(
    val type: String = "response.output_text.delta",
    val delta: String,
    val outputIndex: Int = 0,
    val contentIndex: Int = 0,
)

data class ResponseCompletedEvent(
    val type: String = "response.completed",
    val response: ResponsesApiResponse,
)
