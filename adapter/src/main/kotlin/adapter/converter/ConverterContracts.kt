package adapter.converter

import adapter.http.StreamingBackendResponse
import adapter.model.ChatCompletionRequest
import adapter.model.ChatCompletionResponse
import adapter.model.ResponsesApiResponse
import adapter.model.ResponsesRequest

interface ResponsesRequestConverter {
    fun convert(request: ResponsesRequest): ChatCompletionRequest
}

interface ResponsesResponseConverter {
    fun convert(request: ResponsesRequest, chatCompletion: ChatCompletionResponse): ResponsesApiResponse
    fun convertStream(request: ResponsesRequest, streaming: StreamingBackendResponse): String
}
