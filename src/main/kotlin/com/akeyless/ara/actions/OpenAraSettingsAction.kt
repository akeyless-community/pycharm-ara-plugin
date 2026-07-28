package com.akeyless.ara.actions

import com.akeyless.ara.settings.AraConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

class OpenAraSettingsAction : AnAction("Akeyless ARA Settings…"), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, AraConfigurable::class.java)
    }
}
