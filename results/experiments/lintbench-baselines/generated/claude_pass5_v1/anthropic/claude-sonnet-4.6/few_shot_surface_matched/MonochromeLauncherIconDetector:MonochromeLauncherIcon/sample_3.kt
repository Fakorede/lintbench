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
import com.android.utils.XmlUtils.getFirstSubTagByName
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ADAPTIVE_ICON)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val monochrome = getFirstSubTagByName(element, TAG_MONOCHROME)
        if (monochrome == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Adaptive icon is missing a `<monochrome>` tag"
            )
        }
    }

    companion object {
        private const val TAG_MONOCHROME = "monochrome"

        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation =
                "The system may use the coloring of the user's chosen wallpaper and theme " +
                "to tint app icons. Providing a `<monochrome>` layer (which will be used " +
                "for tinting) for every adaptive icon is strongly recommended, otherwise " +
                "Android 16 QPR 2 and above will simply tint the color version of the icon, " +
                "which may look unusual. Devices running earlier Android versions will (with " +
                "no monochrome layer) show the untinted color icon for your app, which will " +
                "look inconsistent.",
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}