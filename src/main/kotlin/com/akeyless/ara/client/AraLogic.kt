package com.akeyless.ara.client

import com.akeyless.ara.client.AraConfig.Companion.API_V2_SUFFIX

object GatewayUrls {
    fun normalizeGatewayInput(input: String): String {
        val base = input.trim().trimEnd('/')
        require(base.isNotEmpty()) { "Gateway URL is required" }
        return base
    }

    /** Config-port base without `/api/v2` (ARA execution + browser login). */
    fun parseAraGatewayUrl(input: String): String {
        val base = normalizeGatewayInput(input)
        return if (base.lowercase().endsWith(API_V2_SUFFIX)) {
            base.dropLast(API_V2_SUFFIX.length)
        } else {
            base
        }
    }

    /** SDK API base with `/api/v2` (auth + list-items). */
    fun parseSdkGatewayUrl(input: String): String {
        val base = normalizeGatewayInput(input)
        return if (base.lowercase().endsWith(API_V2_SUFFIX)) {
            base
        } else {
            "$base$API_V2_SUFFIX"
        }
    }
}

object AraProducerTypes {
    val DB = setOf(
        "mysql", "postgres", "mssql", "oracle", "snowflake",
        "hana", "redshift", "mongodb", "redis", "cassandra",
    )
    val SERVICE = setOf("aws", "gcp", "azure", "k8s", "eks", "gke", "github")

    /** Item target types the Gateway runs remote commands on over SSH or WinRM (Go: types.ARARemoteProducerTypes). */
    val REMOTE = setOf("ssh", "windows", "rdp", "ldap", "linked")
    const val CUSTOM_MCP = "custom-mcp"

    /** secret_type for SSH certificate issuers; the Gateway signs a short-lived certificate per remote call. */
    const val SSH_CERT_ISSUER = "ssh-cert-issuer"

    fun isDb(producerType: String): Boolean = producerType.lowercase() in DB

    fun isService(producerType: String): Boolean {
        val n = producerType.lowercase()
        return n in SERVICE || n == CUSTOM_MCP
    }

    fun isRemote(producerType: String): Boolean = producerType.lowercase() in REMOTE

    fun isSupported(producerType: String): Boolean {
        val n = producerType.lowercase()
        return n in DB || n in SERVICE || n in REMOTE
    }
}

object AraListBodies {
    val ARA_LIST_ITEM_TYPES = listOf("dynamic-secret", "rotated-secret", "static-secret", AraProducerTypes.SSH_CERT_ISSUER)

    fun buildAraListItemsBody(token: String, path: String? = null): Map<String, Any?> {
        val body = linkedMapOf<String, Any?>(
            "token" to token,
            "filter" to "",
            "type" to ARA_LIST_ITEM_TYPES,
            "ara-only" to true,
            "advanced-filter" to "",
        )
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            body["path"] = trimmed
        }
        return body
    }

    fun buildDiagnosticListItemsBody(token: String, path: String? = null): Map<String, Any?> {
        val body = linkedMapOf<String, Any?>(
            "token" to token,
            "filter" to "",
            "type" to ARA_LIST_ITEM_TYPES,
            "ara-only" to false,
            "advanced-filter" to "",
        )
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            body["path"] = trimmed
        }
        return body
    }
}

fun normalizeItemType(raw: String?): String =
    (raw ?: "").trim().lowercase().replace('_', '-')

@Suppress("UNCHECKED_CAST")
fun mapAraSecrets(items: List<Map<String, Any?>>): List<AraSecretSummary> {
    val results = mutableListOf<AraSecretSummary>()
    for (item in items) {
        val name = (item["item_name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) continue

        val permissions = item["client_permissions"] as? List<*> ?: emptyList<Any?>()
        if (!permissions.contains("ara_allow_access")) continue

        val itemType = item["item_type"] as? String ?: ""
        when {
            normalizeItemType(itemType) == "dynamic-secret" -> {
                val targetType = extractDynamicProducerType(item)
                if (targetType == null || !AraProducerTypes.isSupported(targetType)) continue
                results += AraSecretSummary(
                    name = name,
                    secretType = "dynamic-secret",
                    targetType = targetType,
                    description = (item["item_metadata"] as? String)?.trim()?.ifEmpty { null },
                )
            }
            normalizeItemType(itemType) == "rotated-secret" -> {
                val assoc = item["item_targets_assoc"] as? List<*>
                val first = assoc?.firstOrNull() as? Map<*, *>
                val targetType = (first?.get("target_type") as? String)?.lowercase()
                if (targetType != null && !AraProducerTypes.isSupported(targetType)) continue
                results += AraSecretSummary(
                    name = name,
                    secretType = "rotated-secret",
                    targetType = targetType,
                    description = (item["item_metadata"] as? String)?.trim()?.ifEmpty { null },
                )
            }
            normalizeItemType(itemType) == "static-secret" -> {
                results += AraSecretSummary(
                    name = name,
                    secretType = "static-secret",
                    targetType = AraProducerTypes.CUSTOM_MCP,
                    description = (item["item_metadata"] as? String)?.trim()?.ifEmpty { null },
                )
            }
            normalizeItemType(itemType) == AraProducerTypes.SSH_CERT_ISSUER -> {
                results += AraSecretSummary(
                    name = name,
                    secretType = AraProducerTypes.SSH_CERT_ISSUER,
                    targetType = "ssh",
                    description = (item["item_metadata"] as? String)?.trim()?.ifEmpty { null },
                )
            }
        }
    }
    return results.sortedBy { it.name }
}

@Suppress("UNCHECKED_CAST")
private fun extractDynamicProducerType(item: Map<String, Any?>): String? {
    val general = item["item_general_info"] as? Map<*, *>
    val fromGeneral =
        ((general?.get("dynamic_secret_producer_details") as? Map<*, *>)
            ?.get("producer_type") as? String)
    val fromTop =
        ((item["dynamic_secret_producer_details"] as? Map<*, *>)
            ?.get("producer_type") as? String)
    return (fromGeneral ?: fromTop)?.trim()?.lowercase()?.ifEmpty { null }
}

fun validateConfig(config: AraConfig) {
    require(config.gatewayUrl.isNotBlank() && config.araGatewayUrl.isNotBlank()) {
        "Gateway URL is required (e.g. https://gw.example.com:8000/api/v2)"
    }
    if (!config.token.isNullOrBlank()) return

    when (config.accessType) {
        AccessType.ACCESS_KEY ->
            require(!config.accessId.isNullOrBlank() && !config.accessKey.isNullOrBlank()) {
                "Access ID and Access Key are required for access_key authentication"
            }
        AccessType.UNIVERSAL_IDENTITY ->
            require(!config.uidToken.isNullOrBlank() || !config.uidTokenFile.isNullOrBlank()) {
                "Universal Identity requires a UID token file or token"
            }
        AccessType.SAML, AccessType.OIDC ->
            require(!config.accessId.isNullOrBlank()) {
                "${config.accessType.wireValue} authentication requires Access ID"
            }
        AccessType.JWT ->
            require(!config.accessId.isNullOrBlank() && !config.jwt.isNullOrBlank()) {
                "Access ID and JWT are required for jwt authentication"
            }
        AccessType.AWS_IAM, AccessType.AZURE_AD, AccessType.GCP ->
            throw IllegalArgumentException(
                "${config.accessType.wireValue} is not supported in this plugin version. " +
                    "Use access_key, saml, oidc, jwt, or universal_identity.",
            )
    }
}

fun expandHome(path: String): String {
    if (path.startsWith("~/") || path == "~") {
        val home = System.getProperty("user.home")
        return if (path == "~") home else home + path.substring(1)
    }
    return path
}
