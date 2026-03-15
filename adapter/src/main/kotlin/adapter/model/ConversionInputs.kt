package adapter.model

import adapter.http.StreamingBackendResponse

data class ChatCompletionResponseAdaptation(
    val request: ResponsesRequest,
    val chatCompletion: ChatCompletionResponse,
)

data class StreamingChatCompletionAdaptation(
    val request: ResponsesRequest,
    val streaming: StreamingBackendResponse,
)
