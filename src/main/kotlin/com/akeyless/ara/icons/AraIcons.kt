package com.akeyless.ara.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object AraIcons {
    @JvmField
    val Logo: Icon = IconLoader.getIcon("/icons/akeyless.svg", AraIcons::class.java)

    @JvmField
    val ToolWindow: Icon = Logo
}
