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
                The system may use the coloring of the user's chosen wallpaper and theme to tint app \
                icons.  Providing a `<monochrome>` layer (which will be used for tinting) for every \
                adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above will simply \
                tint the color version of the icon, which may look unusual.  Devices running earlier \
                Android versions will (with no monochrome layer) show the untinted color icon for your \
                app, which will look inconsistent.
            """,
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TAG_ADAPTIVE_ICON = "adaptive-icon"
        private const val TAG_MONOCHROME = "monochrome"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ADAPTIVE_ICON)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check if this adaptive-icon element has a monochrome child element
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.tagName == TAG_MONOCHROME) {
                // Monochrome layer is present, no issue
                return
            }
        }

        // No monochrome layer found, report the issue
        context.report(
            issue = ISSUE,
            element = element,
            location = context.getNameLocation(element),
            message = "The `<adaptive-icon>` does not have a `<monochrome>` layer; " +
                "without it, the icon may look unusual when the system applies theme tinting",
        )
    }
}