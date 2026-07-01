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

    companion object {
        private const val TAG_ADAPTIVE_ICON = "adaptive-icon"
        private const val TAG_MONOCHROME = "monochrome"
        private const val FOLDER_PREFIX_MIPMAP = "mipmap"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons.
                Providing a <code>&lt;monochrome&gt;</code> layer for every adaptive icon is strongly recommended.
                Without a <code>&lt;monochrome&gt;</code> layer, Android 16 QPR 2 and above will tint the color version
                of the icon, which may look unusual, while older devices will show the untinted color icon,
                resulting in an inconsistent look.
            """,
            moreInfo = "https://developer.android.com/develop/ui/views/launch/icon_design_adaptive",
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ADAPTIVE_ICON)

    override fun visitElement(context: XmlContext, element: Element) {
        val folderName = context.file.parentFile?.name ?: return
        if (!folderName.startsWith(FOLDER_PREFIX_MIPMAP)) {
            return
        }

        if (!element.hasElementChild(TAG_MONOCHROME)) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing `<monochrome>` layer in adaptive icon; add a `<monochrome>` layer to support wallpaper/theme icon tinting."
            )
        }
    }

    private fun Element.hasElementChild(tagName: String): Boolean {
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) {
                return true
            }
        }
        return false
    }
}