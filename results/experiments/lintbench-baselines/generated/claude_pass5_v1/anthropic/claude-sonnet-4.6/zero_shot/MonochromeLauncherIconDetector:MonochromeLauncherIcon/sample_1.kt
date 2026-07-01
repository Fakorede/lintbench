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

class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_MONOCHROME = "monochrome"

        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint \
                app icons. Providing a `<monochrome>` layer (which will be used for tinting) for \
                every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above \
                will simply tint the color version of the icon, which may look unusual. Devices \
                running earlier Android versions will (with no monochrome layer) show the untinted \
                color icon for your app, which will look inconsistent.
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
        val children = element.childNodes
        var hasMonochrome = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_MONOCHROME) {
                hasMonochrome = true
                break
            }
        }

        if (!hasMonochrome) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "Adaptive icon does not have a `<monochrome>` tag"
            )
        }
    }
}