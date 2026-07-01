package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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
                Providing a `<monochrome>` layer (which will be used for tinting) for every adaptive icon is \
                strongly recommended, otherwise Android 16 QPR 2 and above will simply tint the color version \
                of the icon, which may look unusual. Devices running earlier Android versions will (with no \
                monochrome layer) show the untinted color icon for your app, which will look inconsistent.
            """.trimIndent(),
            category = Category.ICON,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("adaptive-icon")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var hasMonochrome = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "monochrome") {
                hasMonochrome = true
                break
            }
        }

        if (!hasMonochrome) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Adaptive icon is missing a `<monochrome>` layer"
            )
        }
    }
}