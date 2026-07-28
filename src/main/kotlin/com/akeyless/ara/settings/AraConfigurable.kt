package com.akeyless.ara.settings

import com.akeyless.ara.client.AccessType
import com.akeyless.ara.client.AraConfig
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class AraConfigurable : SearchableConfigurable {
    private var panel: AraSettingsPanel? = null

    override fun getId(): String = "com.akeyless.ara.settings"

    override fun getDisplayName(): String = "Akeyless ARA"

    override fun createComponent(): JComponent {
        val p = AraSettingsPanel()
        panel = p
        reset()
        return p.getPanel()
    }

    override fun isModified(): Boolean {
        val p = panel ?: return false
        val s = AraSettings.getInstance()
        return p.gatewayUrl != s.state.gatewayUrl ||
            p.accessType != s.state.accessType ||
            p.accessId != s.state.accessId ||
            p.uidTokenFile != s.state.uidTokenFile ||
            p.agentId != s.state.agentId ||
            p.defaultSecretName != s.state.defaultSecretName ||
            p.autoConnect != s.state.autoConnect ||
            p.accessKeyChanged ||
            p.jwtChanged
    }

    override fun apply() {
        val p = panel ?: return
        val s = AraSettings.getInstance()
        s.state.gatewayUrl = p.gatewayUrl
        s.state.accessType = p.accessType
        s.state.accessId = p.accessId
        s.state.uidTokenFile = p.uidTokenFile
        s.state.agentId = p.agentId.ifBlank { AraConfig.DEFAULT_AGENT_ID }
        s.state.defaultSecretName = p.defaultSecretName
        s.state.autoConnect = p.autoConnect
        // Blank after edit means "keep stored value" (PasswordSafe).
        if (p.accessKeyChanged && p.accessKey.isNotBlank()) {
            s.setAccessKey(p.accessKey)
        }
        if (p.jwtChanged && p.jwt.isNotBlank()) {
            s.setJwt(p.jwt)
        }
        p.clearSecretChangeFlags()
    }

    override fun reset() {
        val p = panel ?: return
        val s = AraSettings.getInstance()
        p.gatewayUrl = s.state.gatewayUrl
        p.accessType = s.state.accessType
        p.accessId = s.state.accessId
        p.uidTokenFile = s.state.uidTokenFile
        p.agentId = s.state.agentId
        p.defaultSecretName = s.state.defaultSecretName
        p.autoConnect = s.state.autoConnect
        p.setAccessKeyPlaceholder(s.getAccessKey() != null)
        p.setJwtPlaceholder(s.getJwt() != null)
        p.clearSecretChangeFlags()
    }

    override fun disposeUIResources() {
        panel = null
    }
}

class AraSettingsPanel {
    private val gatewayField = JBTextField()
    private val accessTypeCombo = ComboBox(
        AccessType.entries
            .filter { it !in listOf(AccessType.AWS_IAM, AccessType.AZURE_AD, AccessType.GCP) }
            .map { it.wireValue }
            .toTypedArray(),
    )
    private val accessIdField = JBTextField()
    private val accessKeyField = JBPasswordField()
    private val uidTokenFileField = JBTextField()
    private val jwtField = JBPasswordField()
    private val agentIdField = JBTextField()
    private val defaultSecretField = JBTextField()
    private val autoConnectCheck = JBCheckBox("Auto-connect on IDE startup")

    var accessKeyChanged: Boolean = false
        private set
    var jwtChanged: Boolean = false
        private set

    init {
        gatewayField.toolTipText = "Gateway address, e.g. https://gw.example.com:8000/api/v2"
        accessTypeCombo.toolTipText = "Authentication method"
        accessKeyField.toolTipText = "Required for access_key. Stored in PasswordSafe."
        jwtField.toolTipText = "Required for jwt. Stored in PasswordSafe."
        accessKeyField.document.addDocumentListener(SimpleListener { accessKeyChanged = true })
        jwtField.document.addDocumentListener(SimpleListener { jwtChanged = true })
    }

    fun getPanel(): JPanel {
        val form = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Akeyless Agentic Runtime Authority"))
            .addLabeledComponent("Gateway URL:", gatewayField, 1, false)
            .addLabeledComponent("Authentication Method:", accessTypeCombo, 1, false)
            .addLabeledComponent("Access ID:", accessIdField, 1, false)
            .addLabeledComponent("Access Key:", accessKeyField, 1, false)
            .addLabeledComponent("UID Token File:", uidTokenFileField, 1, false)
            .addLabeledComponent("JWT:", jwtField, 1, false)
            .addLabeledComponent("Agent ID:", agentIdField, 1, false)
            .addLabeledComponent("Default secret path:", defaultSecretField, 1, false)
            .addComponent(autoConnectCheck, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        val wrapper = JPanel(BorderLayout())
        wrapper.add(form, BorderLayout.NORTH)
        return wrapper
    }

    var gatewayUrl: String
        get() = gatewayField.text.trim()
        set(value) { gatewayField.text = value }

    var accessType: String
        get() = (accessTypeCombo.selectedItem as? String) ?: AccessType.ACCESS_KEY.wireValue
        set(value) { accessTypeCombo.selectedItem = value }

    var accessId: String
        get() = accessIdField.text.trim()
        set(value) { accessIdField.text = value }

    val accessKey: String
        get() = String(accessKeyField.password)

    val jwt: String
        get() = String(jwtField.password)

    var uidTokenFile: String
        get() = uidTokenFileField.text.trim()
        set(value) { uidTokenFileField.text = value }

    var agentId: String
        get() = agentIdField.text.trim()
        set(value) { agentIdField.text = value }

    var defaultSecretName: String
        get() = defaultSecretField.text.trim()
        set(value) { defaultSecretField.text = value }

    var autoConnect: Boolean
        get() = autoConnectCheck.isSelected
        set(value) { autoConnectCheck.isSelected = value }

    fun setAccessKeyPlaceholder(hasStored: Boolean) {
        accessKeyField.emptyText.text = if (hasStored) "(stored — leave blank to keep)" else ""
        accessKeyField.text = ""
    }

    fun setJwtPlaceholder(hasStored: Boolean) {
        jwtField.emptyText.text = if (hasStored) "(stored — leave blank to keep)" else ""
        jwtField.text = ""
    }

    fun clearSecretChangeFlags() {
        accessKeyChanged = false
        jwtChanged = false
    }
}

private class SimpleListener(private val onChange: () -> Unit) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}
