package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            MonochromeLauncherIconDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint
                app icons. Providing a <monochrome> layer for every adaptive launcher icon is
                strongly recommended; otherwise Android 16 QPR 2 and above will tint the color
                version of the icon, which may look unusual, and earlier devices will show the
                untinted color icon, resulting in an inconsistent appearance.
            """.trimIndent(),
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.MIPMAP

    override fun getApplicableElements(): Collection<String>? =
        listOf("adaptive-icon")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "adaptive-icon") {
            return
        }

        if (!hasMonochromeChild(element)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing <monochrome> layer in adaptive launcher icon",
            )
        }
    }

    private fun hasMonochromeChild(element: org.w3c.dom.Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element && child.tagName == "monochrome") {
                return true
            }
        }
        return false
    }
}