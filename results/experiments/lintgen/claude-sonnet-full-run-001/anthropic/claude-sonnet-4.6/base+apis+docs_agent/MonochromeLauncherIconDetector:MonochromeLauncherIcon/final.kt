package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_ADAPTIVE_ICON
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_MONOCHROME = "monochrome"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ADAPTIVE_ICON)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return

        if (folderType != ResourceFolderType.MIPMAP && folderType != ResourceFolderType.DRAWABLE) {
            return
        }

        // Check if this adaptive icon has a <monochrome> child element
        if (hasMonochromeChild(element)) {
            return
        }

        // Check if there's a separate resource file in another folder that has monochrome
        if (hasSeparateMonochromeResource(context)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Adaptive icon `${context.file.nameWithoutExtension}` does not have a " +
                    "`<monochrome>` layer; this is strongly recommended for proper " +
                    "themed icon support"
        )
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

    /**
     * Checks whether there's a separate resource file (e.g., in another density/API folder)
     * that defines the monochrome layer for this adaptive icon.
     */
    private fun hasSeparateMonochromeResource(context: XmlContext): Boolean {
        val currentFile = context.file
        val resourceDir = currentFile.parentFile?.parentFile ?: return false
        val fileName = currentFile.name

        val siblingFolders = resourceDir.listFiles() ?: return false
        for (folder in siblingFolders) {
            if (!folder.isDirectory) continue
            if (folder == currentFile.parentFile) continue

            val folderName = folder.name
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
            content.contains("<monochrome")
        } catch (e: Exception) {
            false
        }
    }
}