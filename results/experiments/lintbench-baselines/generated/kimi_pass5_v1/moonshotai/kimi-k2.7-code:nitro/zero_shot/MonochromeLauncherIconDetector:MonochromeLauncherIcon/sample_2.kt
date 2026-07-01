package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf("adaptive-icon")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.MIPMAP) {
            return
        }

        if (element.hasChild("monochrome")) {
            return
        }

        context.report(
            ISSUE,
            context.getLocation(element),
            "The adaptive launcher icon is missing a `<monochrome>` layer. " +
                "Add a `<monochrome>` layer so the icon can be tinted consistently with the user's wallpaper and theme."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons.
                Providing a `<monochrome>` layer for every adaptive launcher icon is strongly recommended.
                Without it, devices running newer versions of Android may tint the color version of the icon,
                which can look unusual, while older devices will show the untinted color icon, which may look inconsistent.
            """,
            category = Category.ICONS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}

private fun Element.hasChild(tagName: String): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
        if (children.item(i).nodeName == tagName) {
            return true
        }
    }
    return false
}