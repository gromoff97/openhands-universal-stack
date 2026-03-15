package adapter.http

import org.http4k.core.Response
import org.http4k.core.Status

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

fun BackendHttpResponse.toHttp4kResponse(): Response {
    var response = Response(Status(statusCode, ""))
        .body(bodyBytes.inputStream(), bodyBytes.size.toLong())

    headers.forEach { (name, values) ->
        if (name.lowercase() !in hopByHopHeaders) {
            values.forEach { value ->
                response = response.header(name, value)
            }
        }
    }

    return response
}
