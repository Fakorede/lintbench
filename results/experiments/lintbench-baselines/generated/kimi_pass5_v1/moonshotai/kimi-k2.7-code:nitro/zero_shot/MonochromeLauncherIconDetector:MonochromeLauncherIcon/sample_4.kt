package com.android.tools.lint.checks

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

    override fun getApplicableElements(): Collection<String> = listOf("adaptive-icon")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!element.hasChildWithTag("monochrome")) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Monochrome icon is not defined"
            )
        }
    }

    private fun Element.hasChildWithTag(tag: String): Boolean {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.localName == tag) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons. Providing a `<monochrome>` layer for every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above will tint the color version of the icon, which may look unusual. Devices running earlier Android versions will show the untinted color icon, which will look inconsistent.
            """,
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}