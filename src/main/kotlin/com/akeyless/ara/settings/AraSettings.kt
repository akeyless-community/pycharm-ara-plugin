package com.akeyless.ara.settings

import com.akeyless.ara.client.AccessType
import com.akeyless.ara.client.AraConfig
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "AkeylessAraSettings", storages = [Storage("akeyless-ara.xml")])
class AraSettings : PersistentStateComponent<AraSettings.State> {

    data class State(
        var gatewayUrl: String = "",
        var accessType: String = AccessType.ACCESS_KEY.wireValue,
        var accessId: String = "",
        var uidTokenFile: String = AraConfig.DEFAULT_UID_TOKEN_FILE,
        var agentId: String = AraConfig.DEFAULT_AGENT_ID,
        var defaultSecretName: String = "",
        var mcpId: String = "",
        var autoConnect: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getAccessKey(): String? =
        PasswordSafe.instance.getPassword(accessKeyAttributes())

    fun setAccessKey(value: String?) {
        PasswordSafe.instance.set(
            accessKeyAttributes(),
            if (value.isNullOrBlank()) null else Credentials("akeyless-ara", value),
        )
    }

    fun getJwt(): String? =
        PasswordSafe.instance.getPassword(jwtAttributes())

    fun setJwt(value: String?) {
        PasswordSafe.instance.set(
            jwtAttributes(),
            if (value.isNullOrBlank()) null else Credentials("akeyless-ara", value),
        )
    }

    private fun accessKeyAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("AkeylessARA", "AccessKey"))

    private fun jwtAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("AkeylessARA", "JWT"))

    companion object {
        fun getInstance(): AraSettings =
            ApplicationManager.getApplication().getService(AraSettings::class.java)
    }
}
