package com.akeyless.ara.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatewayUrlsTest {
    @Test
    fun `parse sdk url adds api v2`() {
        assertEquals(
            "https://gw.example.com:8000/api/v2",
            GatewayUrls.parseSdkGatewayUrl("https://gw.example.com:8000"),
        )
    }

    @Test
    fun `parse sdk url keeps api v2`() {
        assertEquals(
            "https://gw.example.com:8000/api/v2",
            GatewayUrls.parseSdkGatewayUrl("https://gw.example.com:8000/api/v2"),
        )
    }

    @Test
    fun `parse ara url strips api v2`() {
        assertEquals(
            "https://gw.example.com:8000",
            GatewayUrls.parseAraGatewayUrl("https://gw.example.com:8000/api/v2"),
        )
    }

    @Test
    fun `parse ara url keeps config port`() {
        assertEquals(
            "https://gw.example.com:8000",
            GatewayUrls.parseAraGatewayUrl("https://gw.example.com:8000"),
        )
    }
}

class AraListBodiesTest {
    @Test
    fun `ara list body includes ara-only and types`() {
        val body = AraListBodies.buildAraListItemsBody("tok")
        assertEquals(true, body["ara-only"])
        assertEquals(listOf("dynamic-secret", "rotated-secret", "static-secret"), body["type"])
        assertEquals("tok", body["token"])
    }

    @Test
    fun `ara list body includes path when provided`() {
        val body = AraListBodies.buildAraListItemsBody("tok", "/prod")
        assertEquals("/prod", body["path"])
    }
}

class MapAraSecretsTest {
    @Test
    fun `maps dynamic secret with ara permission`() {
        val items = listOf(
            mapOf(
                "item_name" to "/db/pg",
                "item_type" to "DYNAMIC_SECRET",
                "client_permissions" to listOf("ara_allow_access"),
                "item_general_info" to mapOf(
                    "dynamic_secret_producer_details" to mapOf("producer_type" to "postgres"),
                ),
            ),
        )
        val secrets = mapAraSecrets(items)
        assertEquals(1, secrets.size)
        assertEquals("/db/pg", secrets[0].name)
        assertEquals("postgres", secrets[0].targetType)
    }

    @Test
    fun `skips without ara permission`() {
        val items = listOf(
            mapOf(
                "item_name" to "/db/pg",
                "item_type" to "dynamic-secret",
                "client_permissions" to listOf("read"),
                "dynamic_secret_producer_details" to mapOf("producer_type" to "postgres"),
            ),
        )
        assertTrue(mapAraSecrets(items).isEmpty())
    }

    @Test
    fun `static secret becomes custom-mcp`() {
        val items = listOf(
            mapOf(
                "item_name" to "/mcp/custom",
                "item_type" to "static-secret",
                "client_permissions" to listOf("ara_allow_access"),
            ),
        )
        val secrets = mapAraSecrets(items)
        assertEquals(AraProducerTypes.CUSTOM_MCP, secrets.single().targetType)
    }
}

class ValidateConfigTest {
    @Test
    fun `access key requires credentials`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateConfig(
                AraConfig(
                    gatewayUrl = "https://gw/api/v2",
                    araGatewayUrl = "https://gw",
                    mcpId = "m",
                    accessType = AccessType.ACCESS_KEY,
                ),
            )
        }
    }

    @Test
    fun `cloud iam deferred`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateConfig(
                AraConfig(
                    gatewayUrl = "https://gw/api/v2",
                    araGatewayUrl = "https://gw",
                    mcpId = "m",
                    accessType = AccessType.AWS_IAM,
                    accessId = "id",
                ),
            )
        }
    }
}

class RedactionTest {
    @Test
    fun `scrubs bearer tokens`() {
        val scrubbed = Redaction.scrubMessage("Authorization Bearer abcdefghijklmnop")
        assertTrue(scrubbed.contains("***"))
        assertTrue(!scrubbed.contains("abcdefghijklmnop"))
    }
}

class AccessTypeParseTest {
    @Test
    fun `api_key maps to access_key`() {
        assertEquals(AccessType.ACCESS_KEY, AccessType.parse("api_key"))
    }
}

class BrowserAuthUrlTest {
    @Test
    fun `builds saml login url`() {
        val url = BrowserAuth.buildAuthUrl(
            "https://gw:8000",
            AccessType.SAML,
            "acc",
            "http://127.0.0.1:9",
        )
        assertTrue(url.contains("/api/saml-login"))
        assertTrue(url.contains("access_id=acc"))
        assertTrue(url.contains("is_use_short_token=true"))
    }
}
