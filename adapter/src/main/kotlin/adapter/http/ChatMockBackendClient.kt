package adapter.http

import adapter.config.AdapterConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.http4k.core.Method
import org.http4k.core.Request
import java.time.Duration

class ChatMockBackendClient(
    private val backendBaseUrl: String = AdapterConfig.backendBaseUrl,
) : BackendClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(600))
        .writeTimeout(Duration.ofSeconds(600))
        .build()

    private val hopByHopHeaders = setOf(
        "connection",
        "content-length",
        "host",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "content-encoding",
    )

    override fun forward(request: Request, path: String): BackendHttpResponse {
        httpRequest(request, path, request.method, body = when (request.method) {
            Method.GET, Method.OPTIONS -> null
            else -> request.bodyString()
        }).use { response ->
            return BackendHttpResponse(
                statusCode = response.code,
                headers = response.headers.toMultimap().mapValues { it.value.toList() },
                bodyBytes = response.body?.bytes() ?: ByteArray(0),
            )
        }
    }

    override fun postJson(request: Request, path: String, jsonBody: String): BackendHttpResponse {
        httpRequest(request, path, Method.POST, jsonBody).use { response ->
            return BackendHttpResponse(
                statusCode = response.code,
                headers = response.headers.toMultimap().mapValues { it.value.toList() },
                bodyBytes = response.body?.bytes() ?: ByteArray(0),
            )
        }
    }

    override fun postJsonStreaming(request: Request, path: String, jsonBody: String): StreamingBackendResponse {
        val response = httpRequest(request, path, Method.POST, jsonBody)
        return StreamingBackendResponse(
            statusCode = response.code,
            headers = response.headers.toMultimap().mapValues { it.value.toList() },
            upstream = response,
        )
    }

    private fun httpRequest(
        request: Request,
        path: String,
        method: Method,
        body: String?,
    ): okhttp3.Response {
        val url = buildBackendUrl(path, request.uri.toString())
        val builder = okhttp3.Request.Builder().url(url)
        copyForwardHeaders(request, builder)

        when (method) {
            Method.GET -> builder.get()
            Method.POST -> {
                val contentType = request.header("Content-Type")?.toMediaTypeOrNull()
                    ?: "application/json".toMediaTypeOrNull()!!
                builder.post((body ?: "").toRequestBody(contentType))
            }
            Method.OPTIONS -> builder.method("OPTIONS", null)
            else -> error("Unsupported method: $method")
        }

        return httpClient.newCall(builder.build()).execute()
    }

    private fun buildBackendUrl(path: String, uri: String): String {
        val query = uri.substringAfter('?', "")
        return if (query.isBlank()) "$backendBaseUrl$path" else "$backendBaseUrl$path?$query"
    }

    private fun copyForwardHeaders(request: Request, builder: okhttp3.Request.Builder) {
        request.headers.forEach { (name, value) ->
            if (value != null && name.lowercase() !in hopByHopHeaders) {
                builder.addHeader(name, value)
            }
        }
    }
}
