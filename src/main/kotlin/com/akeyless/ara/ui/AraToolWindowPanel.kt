package com.akeyless.ara.ui

import com.akeyless.ara.client.AraSecretSummary
import com.akeyless.ara.client.OAuthAuthorizationRequiredException
import com.akeyless.ara.client.Redaction
import com.akeyless.ara.client.formatOAuthRequiredMessage
import com.akeyless.ara.settings.AraClientFactory
import com.akeyless.ara.settings.AraConfigurable
import com.google.gson.GsonBuilder
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.table.DefaultTableModel

class AraToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val statusLabel = JBLabel("Configure Settings → Tools → Akeyless ARA, then use the tabs below.")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val secretsModel = DefaultTableModel(arrayOf("Path", "Type", "Target", "Description"), 0)
    private val secretsTable = JBTable(secretsModel)

    private val querySecretField = JBTextField()
    private val queryPayloadField = JBTextArea(6, 40)
    private val queryAuthCodeField = JBTextField()
    private val queryStateField = JBTextField()
    private val queryResultArea = JBTextArea(12, 40).apply { isEditable = false }

    private val serviceSecretField = JBTextField()
    private val servicePayloadField = JBTextArea(6, 40)
    private val serviceAuthCodeField = JBTextField()
    private val serviceStateField = JBTextField()
    private val serviceResultArea = JBTextArea(12, 40).apply { isEditable = false }

    private val subToolsSecretField = JBTextField()
    private val subToolsResultArea = JBTextArea(12, 40).apply { isEditable = false }

    init {
        border = JBUI.Borders.empty(8)
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Refresh secrets").also { it.addActionListener { refreshSecrets() } })
            add(JButton("Settings").also {
                it.addActionListener {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AraConfigurable::class.java)
                }
            })
        }
        add(toolbar, BorderLayout.NORTH)

        val tabs = JTabbedPane().apply {
            addTab("Secrets", buildSecretsTab())
            addTab("Query DB", buildQueryTab())
            addTab("Service Execute", buildServiceTab())
            addTab("List Sub-Tools", buildSubToolsTab())
        }
        add(tabs, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun buildSecretsTab(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(secretsTable), BorderLayout.CENTER)
        return panel
    }

    private fun buildQueryTab(): JPanel {
        queryPayloadField.lineWrap = true
        val form = formPanel(
            "Secret path:" to querySecretField,
            "Query / payload:" to JBScrollPane(queryPayloadField),
            "OAuth auth-code (optional):" to queryAuthCodeField,
            "OAuth state (optional):" to queryStateField,
        )
        val run = JButton("Run query-db").also {
            it.addActionListener {
                runTargetQuery(
                    secretField = querySecretField,
                    payloadArea = queryPayloadField,
                    authCodeField = queryAuthCodeField,
                    stateField = queryStateField,
                    resultArea = queryResultArea,
                )
            }
        }
        return borderedResult(form, run, queryResultArea)
    }

    private fun buildServiceTab(): JPanel {
        servicePayloadField.lineWrap = true
        val form = formPanel(
            "Secret path:" to serviceSecretField,
            "Payload (JSON / command):" to JBScrollPane(servicePayloadField),
            "OAuth auth-code (optional):" to serviceAuthCodeField,
            "OAuth state (optional):" to serviceStateField,
        )
        val run = JButton("Run service-execute").also {
            it.addActionListener {
                runTargetQuery(
                    secretField = serviceSecretField,
                    payloadArea = servicePayloadField,
                    authCodeField = serviceAuthCodeField,
                    stateField = serviceStateField,
                    resultArea = serviceResultArea,
                )
            }
        }
        return borderedResult(form, run, serviceResultArea)
    }

    private fun buildSubToolsTab(): JPanel {
        val form = formPanel("Secret path:" to subToolsSecretField)
        val run = JButton("List sub-tools").also { it.addActionListener { runListSubTools() } }
        return borderedResult(form, run, subToolsResultArea)
    }

    private fun formPanel(vararg rows: Pair<String, java.awt.Component>): JPanel {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            insets = JBUI.insets(2)
        }
        var y = 0
        for ((label, component) in rows) {
            c.gridy = y++
            panel.add(JBLabel(label), c)
            c.gridy = y++
            panel.add(component, c)
        }
        return panel
    }

    private fun borderedResult(form: JPanel, run: JButton, result: JBTextArea): JPanel {
        val top = JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(run) }, BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            add(top, BorderLayout.NORTH)
            add(JBScrollPane(result), BorderLayout.CENTER)
        }
    }

    private fun refreshSecrets() {
        statusLabel.text = "Listing ARA secrets…"
        object : SwingWorker<List<AraSecretSummary>, Void>() {
            override fun doInBackground(): List<AraSecretSummary> {
                val client = AraClientFactory.createFromSettings()
                return client.listAraSecrets()
            }

            override fun done() {
                try {
                    val secrets = get()
                    secretsModel.rowCount = 0
                    for (s in secrets) {
                        secretsModel.addRow(
                            arrayOf(s.name, s.secretType, s.targetType ?: "", s.description ?: ""),
                        )
                    }
                    statusLabel.text = "Loaded ${secrets.size} ARA secret(s)."
                    if (secrets.isNotEmpty() && querySecretField.text.isBlank()) {
                        val first = secrets.first().name
                        querySecretField.text = first
                        serviceSecretField.text = first
                        subToolsSecretField.text = first
                    }
                } catch (e: Exception) {
                    showError("list-secrets failed", e)
                }
            }
        }.execute()
    }

    private fun runTargetQuery(
        secretField: JBTextField,
        payloadArea: JBTextArea,
        authCodeField: JBTextField,
        stateField: JBTextField,
        resultArea: JBTextArea,
    ) {
        val secret = secretField.text.trim()
        val payload = payloadArea.text.trim()
        if (secret.isEmpty() || payload.isEmpty()) {
            Messages.showWarningDialog(project, "Secret path and payload are required.", "Akeyless ARA")
            return
        }
        statusLabel.text = "Executing…"
        resultArea.text = ""
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                val client = AraClientFactory.createFromSettings()
                val result = client.executeTargetQuery(
                    secretName = secret,
                    payload = payload,
                    authCode = authCodeField.text.trim().ifBlank { null },
                    state = stateField.text.trim().ifBlank { null },
                )
                return gson.toJson(result.raw ?: result)
            }

            override fun done() {
                try {
                    resultArea.text = get()
                    statusLabel.text = "Done."
                } catch (e: Exception) {
                    handleExecuteError(e, resultArea, authCodeField, stateField)
                }
            }
        }.execute()
    }

    private fun runListSubTools() {
        val secret = subToolsSecretField.text.trim()
        if (secret.isEmpty()) {
            Messages.showWarningDialog(project, "Secret path is required.", "Akeyless ARA")
            return
        }
        statusLabel.text = "Listing sub-tools…"
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                val client = AraClientFactory.createFromSettings()
                val result = client.listSubTools(secret)
                return gson.toJson(result.raw ?: result)
            }

            override fun done() {
                try {
                    subToolsResultArea.text = get()
                    statusLabel.text = "Done."
                } catch (e: Exception) {
                    showError("list-sub-tools failed", e)
                }
            }
        }.execute()
    }

    private fun handleExecuteError(
        e: Exception,
        resultArea: JBTextArea,
        authCodeField: JBTextField,
        stateField: JBTextField,
    ) {
        val cause = unwrap(e)
        if (cause is OAuthAuthorizationRequiredException) {
            val msg = formatOAuthRequiredMessage(cause)
            resultArea.text = msg
            stateField.text = cause.state
            statusLabel.text = "OAuth authorization required."
            val open = Messages.showYesNoDialog(
                project,
                "OAuth authorization is required. Open the authorization URL in a browser?",
                "Akeyless ARA",
                Messages.getQuestionIcon(),
            )
            if (open == Messages.YES) {
                BrowserUtil.browse(cause.authorizationUrl)
                Messages.showInfoMessage(
                    project,
                    "After authorizing, paste the auth-code into the form and run again (state is pre-filled).",
                    "Akeyless ARA",
                )
            }
            return
        }
        showError("Execution failed", e)
        resultArea.text = Redaction.scrubMessage(cause.message ?: e.toString())
    }

    private fun showError(title: String, e: Exception) {
        val cause = unwrap(e)
        val message = Redaction.scrubMessage(cause.message ?: e.toString())
        statusLabel.text = title
        SwingUtilities.invokeLater {
            Messages.showErrorDialog(project, message, "Akeyless ARA — $title")
        }
    }

    private fun unwrap(e: Exception): Throwable {
        var cur: Throwable = e
        while (cur.cause != null && cur.cause !== cur) {
            // SwingWorker wraps in ExecutionException
            if (cur.javaClass.name.contains("ExecutionException") || cur is java.util.concurrent.ExecutionException) {
                cur = cur.cause!!
                continue
            }
            break
        }
        return cur
    }
}
