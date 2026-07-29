package com.akeyless.ara.client

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** HTTP header used by Auth for product-scoped client registration. */
const val AKEYLESS_PRODUCT_HEADER = "Akeyless-Product"

/** Product value for Agentic Runtime Authority / Secretless AI. */
const val AKEYLESS_PRODUCT_ARA = "ara"

class HttpJsonClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val gson: Gson = Gson(),
) {
    fun postJson(
        url: String,
        body: Any?,
        headers: Map<String, String> = emptyMap(),
    ): JsonHttpResponse {
        val payload = when (body) {
            null -> "{}"
            is String -> body
            else -> gson.toJson(body)
        }
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
        headers.forEach { (k, v) -> builder.header(k, v) }

        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return JsonHttpResponse(response.statusCode(), response.body().orEmpty())
    }

    fun toMap(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    fun toJsonElement(json: String): JsonElement =
        if (json.isBlank()) JsonObject() else JsonParser.parseString(json)

    fun toJson(value: Any?): String = gson.toJson(value)
}

data class JsonHttpResponse(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

object GatewayAuthHeader {
    fun build(session: AuthSession): Map<String, String> {
        val creds = session.creds
        if (creds != null) {
            val encoded = Base64.getEncoder().encodeToString(
                Gson().toJson(creds).toByteArray(Charsets.UTF_8),
            )
            return mapOf("Authorization" to "Bearer $encoded")
        }
        val token = session.token.trim()
        require(token.isNotEmpty()) { "Unable to build gateway authorization header" }
        return mapOf("Authorization" to "Bearer $token")
    }
}

object Redaction {
    private val SENSITIVE = listOf(
        "access-key", "access_key", "token", "jwt", "uid-token", "password",
        "authorization", "secret", "creds",
    )

    fun scrubMessage(message: String): String {
        var out = message
        // Drop long bearer tokens from messages
        out = out.replace(Regex("""Bearer\s+[A-Za-z0-9._\-+/=]{12,}"""), "Bearer ***")
        for (key in SENSITIVE) {
            out = out.replace(
                Regex("""(?i)("$key"\s*:\s*")([^"]{4,})(")"""),
                """$1***$3""",
            )
        }
        return out
    }
}
