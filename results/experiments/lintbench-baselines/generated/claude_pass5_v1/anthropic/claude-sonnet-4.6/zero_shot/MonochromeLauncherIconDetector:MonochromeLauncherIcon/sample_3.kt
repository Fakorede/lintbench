package com.android.tools.lint.checks

import com.android.SdkConstants.ADAPTIVE_ICON_XML_ELEMENT
import com.android.SdkConstants.TAG_ADAPTIVE_ICON
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Detector that checks whether adaptive launcher icons define a monochrome layer.
 *
 * Android 12 (API 33+) supports monochrome adaptive icons, which allows the system to
 * tint app icons based on the user's wallpaper and theme. Without a monochrome layer,
 * Android 16 QPR2+ will tint the color icon directly, which may look unusual.
 */
class MonochromeLauncherIconDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_MONOCHROME = "monochrome"

        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to \
                tint app icons. Providing a `<monochrome>` layer (which will be used for \
                tinting) for every adaptive icon is strongly recommended, otherwise Android \
                16 QPR 2 and above will simply tint the color version of the icon, which \
                may look unusual. Devices running earlier Android versions will (with no \
                monochrome layer) show the untinted color icon for your app, which will \
                look inconsistent.
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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Only process adaptive icon XML files
        if (root.tagName != TAG_ADAPTIVE_ICON) {
            return
        }

        // Check if there is a monochrome child element
        val children = root.childNodes
        var hasMonochrome = false
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == TAG_MONOCHROME) {
                hasMonochrome = true
                break
            }
        }

        if (!hasMonochrome) {
            val location = context.getLocation(root)
            context.report(
                issue = ISSUE,
                location = location,
                message = "Adaptive icon `${context.file.name}` does not have a `<monochrome>` tag"
            )
        }
    }
}