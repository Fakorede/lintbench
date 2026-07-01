package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf("adaptive-icon")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var hasMonochrome = false
        var child = element.firstChild
        while (child != null) {
            if (child is Element && child.tagName == "monochrome") {
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