package com.akeyless.ara.client

enum class AccessType(val wireValue: String) {
    ACCESS_KEY("access_key"),
    AWS_IAM("aws_iam"),
    AZURE_AD("azure_ad"),
    GCP("gcp"),
    UNIVERSAL_IDENTITY("universal_identity"),
    JWT("jwt"),
    SAML("saml"),
    OIDC("oidc");

    companion object {
        fun parse(raw: String?): AccessType {
            val value = (raw ?: "access_key").trim().lowercase()
            if (value == "api_key") return ACCESS_KEY
            return entries.find { it.wireValue == value }
                ?: throw IllegalArgumentException(
                    "Unsupported access type \"$raw\". Expected access_key, aws_iam, azure_ad, gcp, universal_identity, jwt, saml, or oidc.",
                )
        }
    }
}

data class AraConfig(
    val gatewayUrl: String,
    val araGatewayUrl: String,
    val defaultSecretName: String? = null,
    val agentId: String = DEFAULT_AGENT_ID,
    val mcpId: String,
    val accessType: AccessType = AccessType.ACCESS_KEY,
    val accessId: String? = null,
    val accessKey: String? = null,
    val uidToken: String? = null,
    val uidTokenFile: String? = null,
    val jwt: String? = null,
    val token: String? = null,
    val tokenExpiryMarginMs: Long = DEFAULT_TOKEN_EXPIRY_MARGIN_MS,
) {
    companion object {
        const val DEFAULT_AGENT_ID = "pycharm"
        const val DEFAULT_TOKEN_EXPIRY_MARGIN_MS = 60_000L
        const val DEFAULT_UID_TOKEN_FILE = "~/.akeyless/uid_rotator/uid-token"
        const val API_V2_SUFFIX = "/api/v2"
    }
}

data class AuthSession(
    val token: String,
    val expiresAtMs: Long,
    val creds: Map<String, Any?>? = null,
)

data class AraSecretSummary(
    val name: String,
    val secretType: String,
    val targetType: String? = null,
    val description: String? = null,
)

data class TargetQueryResult(
    val targetType: String? = null,
    val results: List<Map<String, Any?>>? = null,
    val raw: Any? = null,
)

data class ListSubToolsTool(
    val name: String,
    val description: String? = null,
    val parameters: Any? = null,
)

data class ListSubToolsResult(
    val targetType: String? = null,
    val tools: List<ListSubToolsTool>? = null,
    val raw: Any? = null,
)

class OAuthAuthorizationRequiredException(
    val authorizationUrl: String,
    val state: String,
) : Exception("OAuth authorization required: $authorizationUrl")
