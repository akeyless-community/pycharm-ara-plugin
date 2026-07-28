package com.akeyless.ara.client

import java.util.UUID

class AraClient(
    private val config: AraConfig,
    private val http: HttpJsonClient = HttpJsonClient(),
) {
    private var session: AuthSession? = null

    init {
        validateConfig(config)
    }

    fun getConfig(): AraConfig = config

    @Synchronized
    private fun getSession(): AuthSession {
        val now = System.currentTimeMillis()
        val current = session
        if (current != null && now < current.expiresAtMs) {
            return current
        }
        val fresh = Auth.authenticate(config, http)
        session = fresh
        return fresh
    }

    fun listAraSecrets(path: String? = null): List<AraSecretSummary> {
        val token = getSession().token
        val body = AraListBodies.buildAraListItemsBody(token, path)
        val items = listItems(body)
        return mapAraSecrets(items)
    }

    fun executeTargetQuery(
        secretName: String,
        payload: String,
        agentId: String? = null,
        authCode: String? = null,
        state: String? = null,
        originalUser: String? = null,
        originalPrompt: String? = null,
    ): TargetQueryResult {
        val authHeader = GatewayAuthHeader.build(getSession())
        val body = linkedMapOf<String, Any?>(
            "secret_name" to secretName,
            "payload" to payload,
            "agent_id" to (agentId ?: config.agentId),
            "mcp_id" to config.mcpId,
        )
        if (!authCode.isNullOrBlank()) body["auth_code"] = authCode
        if (!state.isNullOrBlank()) body["state"] = state
        if (!originalUser.isNullOrBlank()) body["original_user"] = originalUser
        if (!originalPrompt.isNullOrBlank()) body["original_prompt"] = originalPrompt

        val parsed = postAraJson("/config/target_query", authHeader, body, "target query")
        return parseTargetQuery(parsed)
    }

    fun listSubTools(secretName: String, agentId: String? = null): ListSubToolsResult {
        val authHeader = GatewayAuthHeader.build(getSession())
        val body = mapOf(
            "secret_name" to secretName,
            "agent_id" to (agentId ?: config.agentId),
            "mcp_id" to config.mcpId,
        )
        val parsed = postAraJson("/config/list_sub_tools", authHeader, body, "list sub-tools")
        return parseListSubTools(parsed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun listItems(body: Map<String, Any?>): List<Map<String, Any?>> {
        val base = config.gatewayUrl.trimEnd('/')
        val response = http.postJson("$base/list-items", body)
        if (!response.ok) {
            throw IllegalStateException(
                Redaction.scrubMessage("list-items failed (${response.status}): ${response.body}"),
            )
        }
        val map = http.toMap(response.body)
        val items = map["items"] as? List<*> ?: return emptyList()
        return items.mapNotNull { it as? Map<String, Any?> }
    }

    private fun postAraJson(
        path: String,
        authHeader: Map<String, String>,
        body: Map<String, Any?>,
        failureLabel: String,
    ): Any? {
        val base = config.araGatewayUrl.trimEnd('/')
        val response = http.postJson("$base$path", body, authHeader)
        val parsed: Any? = try {
            if (response.body.isBlank()) emptyMap<String, Any?>()
            else http.toMap(response.body)
        } catch (_: Exception) {
            mapOf("raw" to response.body)
        }

        if (!response.ok) {
            if (response.status == 401 && parsed is Map<*, *>) {
                val url = parsed["authorization_url"] as? String
                val state = parsed["state"] as? String
                if (!url.isNullOrBlank() && !state.isNullOrBlank()) {
                    throw OAuthAuthorizationRequiredException(url, state)
                }
            }
            val message = when {
                parsed is Map<*, *> && parsed["error"] is String -> parsed["error"] as String
                response.body.isNotBlank() -> response.body
                else -> "Gateway $failureLabel failed (${response.status})"
            }
            throw IllegalStateException(Redaction.scrubMessage(message))
        }
        return parsed
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTargetQuery(parsed: Any?): TargetQueryResult {
        if (parsed is Map<*, *>) {
            val results = (parsed["results"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }
            return TargetQueryResult(
                targetType = parsed["target_type"] as? String,
                results = results,
                raw = parsed,
            )
        }
        return TargetQueryResult(raw = parsed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseListSubTools(parsed: Any?): ListSubToolsResult {
        if (parsed is Map<*, *>) {
            val tools = (parsed["tools"] as? List<*>)?.mapNotNull { entry ->
                val m = entry as? Map<*, *> ?: return@mapNotNull null
                ListSubToolsTool(
                    name = m["name"] as? String ?: return@mapNotNull null,
                    description = m["description"] as? String,
                    parameters = m["parameters"],
                )
            }
            return ListSubToolsResult(
                targetType = parsed["target_type"] as? String,
                tools = tools,
                raw = parsed,
            )
        }
        return ListSubToolsResult(raw = parsed)
    }

    companion object {
        fun fromSettings(
            gatewayInput: String,
            accessType: AccessType,
            accessId: String?,
            accessKey: String?,
            uidTokenFile: String?,
            jwt: String?,
            agentId: String,
            defaultSecretName: String?,
            mcpId: String = UUID.randomUUID().toString(),
        ): AraClient {
            val config = AraConfig(
                gatewayUrl = GatewayUrls.parseSdkGatewayUrl(gatewayInput),
                araGatewayUrl = GatewayUrls.parseAraGatewayUrl(gatewayInput),
                defaultSecretName = defaultSecretName?.ifBlank { null },
                agentId = agentId.ifBlank { AraConfig.DEFAULT_AGENT_ID },
                mcpId = mcpId,
                accessType = accessType,
                accessId = accessId?.ifBlank { null },
                accessKey = accessKey?.ifBlank { null },
                uidTokenFile = uidTokenFile?.ifBlank { null } ?: AraConfig.DEFAULT_UID_TOKEN_FILE,
                jwt = jwt?.ifBlank { null },
            )
            return AraClient(config)
        }
    }
}

fun formatOAuthRequiredMessage(error: OAuthAuthorizationRequiredException): String =
    "This secret requires OAuth authorization. " +
        "Open the following URL in your browser to authorize, " +
        "then retry with the auth-code and state parameters.\n\n" +
        "Authorization URL: ${error.authorizationUrl}\nState: ${error.state}"
