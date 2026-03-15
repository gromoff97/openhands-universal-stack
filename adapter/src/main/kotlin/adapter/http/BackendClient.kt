package adapter.http

import org.http4k.core.Request

interface BackendClient {
    fun forward(request: Request, path: String): BackendHttpResponse
    fun postJson(request: Request, path: String, jsonBody: String): BackendHttpResponse
    fun postJsonStreaming(request: Request, path: String, jsonBody: String): StreamingBackendResponse
}
