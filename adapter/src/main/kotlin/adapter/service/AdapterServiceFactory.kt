package adapter.service

import adapter.converter.Converter
import adapter.http.ChatMockBackendClient
import adapter.model.ChatCompletionRequest
import adapter.model.ChatCompletionResponseAdaptation
import adapter.model.ResponsesApiResponse
import adapter.model.ResponsesRequest
import adapter.model.StreamingChatCompletionAdaptation

object AdapterServiceFactory {
    fun create(
        backendBaseUrl: String,
        requestConverter: Converter<ResponsesRequest, ChatCompletionRequest>,
        responseConverter: Converter<ChatCompletionResponseAdaptation, ResponsesApiResponse>,
        streamingResponseConverter: Converter<StreamingChatCompletionAdaptation, String>,
    ): AdapterService =
        AdapterService(
            backendClient = ChatMockBackendClient(backendBaseUrl),
            requestConverter = requestConverter,
            responseConverter = responseConverter,
            streamingResponseConverter = streamingResponseConverter,
        )
}
