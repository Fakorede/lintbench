package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons. \
                Providing a `<monochrome>` layer (which will be used for tinting) for every adaptive icon is strongly recommended, \
                otherwise Android 16 QPR 2 and above will simply tint the color version of the icon, which may look unusual. \
                Devices running earlier Android versions will (with no monochrome layer) show the untinted color icon for your app, \
                which will look inconsistent.
            """.trimIndent(),
            category = Category.ICONS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("adaptive-icon")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "adaptive-icon") return

        var hasMonochrome = false
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "monochrome") {
                hasMonochrome = true
                break
            }
            child = child.nextSibling
        }

        if (!hasMonochrome) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "The `<adaptive-icon>` is missing a `<monochrome>` tag"
            )
        }
    }
}