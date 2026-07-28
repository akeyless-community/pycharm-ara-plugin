package com.akeyless.ara.settings

import com.akeyless.ara.client.AccessType
import com.akeyless.ara.client.AraClient
import com.akeyless.ara.client.AraConfig
import java.util.UUID

object AraClientFactory {
    fun createFromSettings(): AraClient {
        val s = AraSettings.getInstance()
        val accessType = AccessType.parse(s.state.accessType)
        val mcpId = s.state.mcpId.ifBlank {
            UUID.randomUUID().toString().also { s.state.mcpId = it }
        }
        val accessKey = when (accessType) {
            AccessType.ACCESS_KEY -> s.getAccessKey()
            else -> null
        }
        val jwt = when (accessType) {
            AccessType.JWT -> s.getJwt()
            else -> null
        }
        return AraClient.fromSettings(
            gatewayInput = s.state.gatewayUrl,
            accessType = accessType,
            accessId = s.state.accessId.ifBlank { null },
            accessKey = accessKey,
            uidTokenFile = s.state.uidTokenFile.ifBlank { AraConfig.DEFAULT_UID_TOKEN_FILE },
            jwt = jwt,
            agentId = s.state.agentId.ifBlank { AraConfig.DEFAULT_AGENT_ID },
            defaultSecretName = s.state.defaultSecretName.ifBlank { null },
            mcpId = mcpId,
        )
    }
}
