package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_ADAPTIVE_ICON
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.io.File

/**
 * Detector that checks whether adaptive launcher icons define a <monochrome> layer.
 */
class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to \
                tint app icons. Providing a `<monochrome>` layer (which will be used for \
                tinting) for every adaptive icon is strongly recommended, otherwise Android \
                16 QPR 2 and above will simply tint the color version of the icon, which may \
                look unusual. Devices running earlier Android versions will (with no monochrome \
                layer) show the untinted color icon for your app, which will look inconsistent.
            """,
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_MONOCHROME = "monochrome"

        /** Mipmap folder prefixes that contain launcher icons */
        private val MIPMAP_FOLDER_PREFIXES = listOf("mipmap")
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ADAPTIVE_ICON)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Only care about adaptive icons in mipmap folders (launcher icons)
        val folderType = context.resourceFolderType ?: return

        // We check both mipmap and drawable folders, but only flag mipmap ones
        // (launcher icons are typically in mipmap, but can also be in drawable)
        if (folderType != ResourceFolderType.MIPMAP && folderType != ResourceFolderType.DRAWABLE) {
            return
        }

        // Check if this adaptive icon is likely a launcher icon
        // For mipmap folders, always check. For drawable folders, only check if referenced
        // from the manifest as a launcher icon (we approximate by checking all adaptive icons
        // in mipmap folders and drawable folders that look like launcher icons).
        if (folderType == ResourceFolderType.DRAWABLE) {
            // Only check drawable adaptive icons if they appear to be launcher icons
            // We check by looking at the file name for common launcher icon names
            val fileName = context.file.nameWithoutExtension
            if (!isLikelyLauncherIconName(fileName)) {
                return
            }
        }

        // Check if there's a <monochrome> child element
        val hasMonochrome = hasMonochromeChild(element)

        if (!hasMonochrome) {
            // Check if there's a separate monochrome resource file for this icon
            // (i.e., a file with the same name in a mipmap-anydpi-v26 or similar folder
            // that has a monochrome layer, or a companion monochrome file)
            if (!hasSeparateMonochromeResource(context)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Adaptive icon `${context.file.nameWithoutExtension}` does not have a " +
                            "`<monochrome>` layer; this is strongly recommended for proper " +
                            "themed icon support"
                )
            }
        }
    }

    private fun hasMonochromeChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_MONOCHROME) {
                return true
            }
        }
        return false
    }

    private fun isLikelyLauncherIconName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("launcher") ||
                lower.contains("ic_launcher") ||
                lower == "icon" ||
                lower.contains("app_icon") ||
                lower.contains("appicon")
    }

    /**
     * Checks whether there's a separate resource file (e.g., in another density/API folder)
     * that defines the monochrome layer for this adaptive icon.
     *
     * This is a heuristic: we look for another XML file with the same name in a sibling
     * resource folder that contains a <monochrome> element.
     */
    private fun hasSeparateMonochromeResource(context: XmlContext): Boolean {
        val currentFile = context.file
        val resourceDir = currentFile.parentFile?.parentFile ?: return false
        val fileName = currentFile.name

        // Look through sibling resource folders for the same file name
        val siblingFolders = resourceDir.listFiles() ?: return false
        for (folder in siblingFolders) {
            if (!folder.isDirectory) continue
            if (folder == currentFile.parentFile) continue

            val folderName = folder.name
            // Only look in mipmap and drawable folders
            if (!folderName.startsWith("mipmap") && !folderName.startsWith("drawable")) continue

            val siblingFile = File(folder, fileName)
            if (siblingFile.exists() && siblingFile.isFile) {
                if (fileContainsMonochromeTag(siblingFile)) {
                    return true
                }
            }
        }
        return false
    }

    private fun fileContainsMonochromeTag(file: File): Boolean {
        return try {
            val content = file.readText()
            content.contains("<monochrome") || content.contains("<monochrome>")
        } catch (e: Exception) {
            false
        }
    }
}