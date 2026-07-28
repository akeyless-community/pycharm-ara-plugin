package com.akeyless.ara.client

import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object BrowserAuth {
    private const val AUTH_TIMEOUT_MS = 2 * 60 * 1000L

    fun buildAuthUrl(
        authBaseUrl: String,
        accessType: AccessType,
        accessId: String,
        redirectUri: String,
    ): String {
        require(accessType == AccessType.SAML || accessType == AccessType.OIDC) {
            "Browser auth only supports saml or oidc"
        }
        val base = authBaseUrl.trimEnd('/')
        val path = if (accessType == AccessType.SAML) "/api/saml-login" else "/api/oidc-login"
        val uri = URI(
            "$base$path?access_id=${encode(accessId)}" +
                "&redirect_uri=${encode(redirectUri)}" +
                "&is_use_short_token=true",
        )
        return uri.toString()
    }

    fun authenticate(
        accessType: AccessType,
        authBaseUrl: String,
        accessId: String,
    ): BrowserAuthResult {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val redirectUri = "http://127.0.0.1:$port"
        val future = CompletableFuture<BrowserAuthResult>()

        val acceptor = thread(name = "akeyless-ara-browser-auth", isDaemon = true) {
            try {
                server.soTimeout = AUTH_TIMEOUT_MS.toInt()
                server.use { ss ->
                    val socket = ss.accept()
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                        val requestLine = input.readLine() ?: ""
                        // drain headers
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val pathAndQuery = requestLine.split(' ').getOrNull(1) ?: "/"
                        val query = pathAndQuery.substringAfter('?', "")
                        val params = parseQuery(query)
                        val error = params["error"]
                        if (error != null) {
                            writeHtml(s.getOutputStream(), 400, "Authentication failed: $error")
                            future.completeExceptionally(IllegalStateException("Authentication failed: $error"))
                            return@thread
                        }
                        val creds = params["creds"]
                        if (creds.isNullOrBlank()) {
                            writeHtml(s.getOutputStream(), 400, "Missing credentials in callback")
                            future.completeExceptionally(
                                IllegalStateException("Authentication callback did not include credentials"),
                            )
                            return@thread
                        }
                        try {
                            val result = parseCredsParam(creds)
                            writeHtml(
                                s.getOutputStream(),
                                200,
                                "<strong>Authentication succeeded.</strong> You may close this tab and return to the IDE.",
                            )
                            future.complete(result)
                        } catch (e: Exception) {
                            writeHtml(s.getOutputStream(), 400, e.message ?: "Invalid credentials")
                            future.completeExceptionally(e)
                        }
                    }
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        try {
            val url = buildAuthUrl(authBaseUrl, accessType, accessId, redirectUri)
            BrowserUtil.browse(url)
            return future.get(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            try {
                server.close()
            } catch (_: Exception) {
            }
            acceptor.join(1000)
        }
    }

    private fun parseCredsParam(creds: String): BrowserAuthResult {
        val decoded = URLDecoder.decode(creds, StandardCharsets.UTF_8)
        val obj = HttpJsonClient().toJsonElement(decoded).asJsonObject
        val token = obj.get("token")?.asString?.trim().orEmpty()
        require(token.isNotEmpty()) { "Authentication response did not include a token" }
        val expiration = obj.get("expiration")?.takeIf { !it.isJsonNull }?.asString
        val credsMap = obj.get("creds")?.takeIf { it.isJsonObject }?.let { element ->
            HttpJsonClient().toMap(element.toString())
        }
        return BrowserAuthResult(token = token, creds = credsMap, expiration = expiration)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8)
            key to value
        }.toMap()
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun writeHtml(out: java.io.OutputStream, status: Int, bodyInner: String) {
        val safe = escapeHtml(bodyInner)
        val body = "<html><body><p>$safe</p></body></html>"
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = if (status in 200..299) "OK" else "Bad Request"
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

data class BrowserAuthResult(
    val token: String,
    val creds: Map<String, Any?>? = null,
    val expiration: String? = null,
)

object Auth {
    private const val ACCESS_KEY_DEFAULT_TTL_MS = 14 * 60 * 1000L
    private const val ENV_TOKEN_TTL_MS = 50 * 60 * 1000L

    fun authenticate(config: AraConfig, http: HttpJsonClient = HttpJsonClient()): AuthSession {
        val now = System.currentTimeMillis()
        if (!config.token.isNullOrBlank()) {
            return AuthSession(token = config.token.trim(), expiresAtMs = now + ENV_TOKEN_TTL_MS)
        }

        if (config.accessType == AccessType.SAML || config.accessType == AccessType.OIDC) {
            val browser = BrowserAuth.authenticate(
                accessType = config.accessType,
                authBaseUrl = config.araGatewayUrl,
                accessId = config.accessId.orEmpty(),
            )
            return AuthSession(
                token = browser.token,
                expiresAtMs = expiryFromBrowser(browser.expiration, config.tokenExpiryMarginMs),
                creds = browser.creds,
            )
        }

        val authBody = when (config.accessType) {
            AccessType.ACCESS_KEY -> mapOf(
                "access-id" to config.accessId,
                "access-type" to "access_key",
                "access-key" to config.accessKey,
            )
            AccessType.UNIVERSAL_IDENTITY -> mapOf(
                "access-type" to "universal_identity",
                "uid-token" to resolveUidToken(config),
            )
            AccessType.JWT -> mapOf(
                "access-id" to config.accessId,
                "access-type" to "jwt",
                "jwt" to config.jwt,
            )
            else -> throw IllegalArgumentException(
                "Unsupported access type for auth API: ${config.accessType.wireValue}",
            )
        }

        val base = config.gatewayUrl.trimEnd('/')
        val response = http.postJson("$base/auth", authBody)
        if (!response.ok) {
            throw IllegalStateException(
                Redaction.scrubMessage("Authentication failed (${response.status}): ${response.body}"),
            )
        }
        val obj = http.toJsonElement(response.body).asJsonObject
        val token = obj.get("token")?.asString?.trim().orEmpty()
        require(token.isNotEmpty()) { "Akeyless authentication did not return a token" }
        val creds = obj.get("creds")?.takeIf { it.isJsonObject }?.let { http.toMap(it.toString()) }
        return AuthSession(
            token = token,
            expiresAtMs = expiryFromAuthOutput(obj, config.tokenExpiryMarginMs),
            creds = creds,
        )
    }

    private fun resolveUidToken(config: AraConfig): String {
        if (!config.uidToken.isNullOrBlank()) return config.uidToken.trim()
        val file = expandHome(config.uidTokenFile ?: AraConfig.DEFAULT_UID_TOKEN_FILE)
        val path = java.nio.file.Path.of(file)
        require(java.nio.file.Files.exists(path)) { "UID token file not found: $file" }
        return java.nio.file.Files.readString(path).trim().also {
            require(it.isNotEmpty()) { "UID token file is empty: $file" }
        }
    }

    private fun expiryFromAuthOutput(obj: JsonObject, marginMs: Long): Long {
        val exp = obj.get("expiration")?.takeIf { !it.isJsonNull }?.asString?.trim()
        val now = System.currentTimeMillis()
        if (exp.isNullOrEmpty()) return now + ACCESS_KEY_DEFAULT_TTL_MS - marginMs
        val asNum = exp.toLongOrNull()
        if (asNum != null && asNum > 1e12) return asNum - marginMs
        return try {
            java.time.Instant.parse(exp).toEpochMilli() - marginMs
        } catch (_: Exception) {
            now + ACCESS_KEY_DEFAULT_TTL_MS - marginMs
        }
    }

    private fun expiryFromBrowser(expiration: String?, marginMs: Long): Long {
        if (!expiration.isNullOrBlank()) {
            val fake = JsonObject().apply { addProperty("expiration", expiration) }
            return expiryFromAuthOutput(fake, marginMs)
        }
        return System.currentTimeMillis() + ENV_TOKEN_TTL_MS
    }
}
