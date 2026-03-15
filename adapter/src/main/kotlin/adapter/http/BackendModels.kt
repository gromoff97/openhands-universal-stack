package adapter.http

data class BackendHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val bodyBytes: ByteArray,
)

data class StreamingBackendResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val upstream: okhttp3.Response,
) : AutoCloseable {
    override fun close() {
        upstream.close()
    }
}
