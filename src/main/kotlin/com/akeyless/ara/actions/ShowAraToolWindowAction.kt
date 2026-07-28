package com.akeyless.ara.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

class ShowAraToolWindowAction : AnAction("Show Akeyless ARA"), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow("Akeyless ARA")
        if (tw == null) {
            return
        }
        tw.show(null)
        tw.activate(null, true)
    }
}
