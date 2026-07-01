package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = setOf(TAG_ADAPTIVE_ICON)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isInMipmapFolder(context)) {
            return
        }

        if (element.getElementsByTagName(TAG_MONOCHROME).length == 0) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This adaptive launcher icon is missing a `<monochrome>` layer"
            )
        }
    }

    private fun isInMipmapFolder(context: XmlContext): Boolean {
        val parentName = context.file.parentFile?.name ?: return false
        return parentName.startsWith("mipmap")
    }

    companion object {
        private const val TAG_ADAPTIVE_ICON = "adaptive-icon"
        private const val TAG_MONOCHROME = "monochrome"

        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons.
                Providing a `<monochrome>` layer (which will be used for tinting) for every adaptive icon is strongly recommended,
                otherwise Android 16 QPR 2 and above will simply tint the color version of the icon, which may look unusual.
                Devices running earlier Android versions will, with no monochrome layer, show the untinted color icon for your app,
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
}