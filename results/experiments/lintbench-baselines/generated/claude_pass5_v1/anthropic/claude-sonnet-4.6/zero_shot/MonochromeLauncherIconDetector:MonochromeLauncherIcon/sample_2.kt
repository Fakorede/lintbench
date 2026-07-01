package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_ADAPTIVE_ICON
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Detector that checks whether adaptive launcher icons define a <monochrome> layer.
 *
 * Without a monochrome layer, Android 16 QPR2+ will tint the full-color icon,
 * which may look unusual. Earlier Android versions will show the untinted color
 * icon, which looks inconsistent compared to apps that do provide a monochrome layer.
 */
class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_MONOCHROME = "monochrome"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme \
                to tint app icons. Providing a `<monochrome>` layer (which will be used \
                for tinting) for every adaptive icon is strongly recommended, otherwise \
                Android 16 QPR 2 and above will simply tint the color version of the icon, \
                which may look unusual. Devices running earlier Android versions will \
                (with no monochrome layer) show the untinted color icon for your app, \
                which will look inconsistent.
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ADAPTIVE_ICON)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check files in the mipmap or drawable directories (already filtered by appliesTo)
        // Look for a <monochrome> child element
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_MONOCHROME) {
                // Found a monochrome element, no issue
                return
            }
        }

        // No <monochrome> child found — report the issue
        context.report(
            issue = ISSUE,
            location = context.getNameLocation(element),
            message = "The adaptive icon does not define a `<monochrome>` layer; " +
                    "monochrome icons are recommended for themed icon support"
        )
    }
}