package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE ||
                folderType == com.android.resources.ResourceFolderType.MIPMAP
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("adaptive-icon")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.getElementsByTagName("monochrome").length == 0) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Monochrome icon is not defined; add a <monochrome> layer to this adaptive icon"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = "The system may use the coloring of the user's chosen wallpaper and theme " +
                    "to tint app icons. Providing a <monochrome> layer (which will be used for tinting) " +
                    "for every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above " +
                    "will simply tint the color version of the icon, which may look unusual. Devices running " +
                    "earlier Android versions will (with no monochrome layer) show the untinted color icon " +
                    "for your app, which will look inconsistent.",
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