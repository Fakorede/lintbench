package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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
                Adaptive icons should define a `<monochrome>` layer so the system can tint the icon
                to match the user's wallpaper and theme. Without it, Android 16 QPR 2 and above
                will tint the color version of the icon, which may look unusual, while older
                devices will show the untinted color icon, leading to an inconsistent look.
                Add a `<monochrome>` element inside this `<adaptive-icon>` referencing a drawable.
            """.trimIndent(),
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP

    override fun getApplicableElements(): Collection<String>? = listOf("adaptive-icon")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!hasMonochromeChild(element)) {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "Adaptive icon is missing a `<monochrome>` layer; add one to support themed app icons.",
            )
        }
    }

    private fun hasMonochromeChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.localName == "monochrome") {
                return true
            }
        }
        return false
    }
}