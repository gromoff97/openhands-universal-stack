package adapter.http

import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

fun corsFilter(): Filter = Filter { next ->
    { request -> applyCors(next(request), request) }
}

fun preflightResponse(): Response = Response(Status.NO_CONTENT)

private fun applyCors(response: Response, request: Request): Response {
    val origin = request.header("Origin") ?: "*"
    return response
        .header("Access-Control-Allow-Origin", origin)
        .header(
            "Access-Control-Allow-Headers",
            "Authorization, Content-Type, X-Requested-With, x-stainless-lang, x-stainless-package-version, x-stainless-os, x-stainless-arch, x-stainless-runtime, x-stainless-runtime-version, OpenAI-Beta",
        )
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        .header("Access-Control-Allow-Credentials", "true")
        .header("Vary", "Origin")
}
