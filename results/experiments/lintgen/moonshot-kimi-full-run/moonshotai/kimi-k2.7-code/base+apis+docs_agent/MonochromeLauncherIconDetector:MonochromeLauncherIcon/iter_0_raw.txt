package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("adaptive-icon")

    override fun visitElement(context: XmlContext, element: Element) {
        val folderName = context.file.parentFile?.name ?: return
        if (!folderName.startsWith("mipmap")) {
            return
        }

        if (element.getElementsByTagName("monochrome").length == 0) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Adaptive launcher icon is missing a `<monochrome>` layer; add one to support adaptive theming"
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint app icons. \
                Providing a `<monochrome>` layer for every adaptive icon is strongly recommended, otherwise \
                Android 16 QPR 2 and above will simply tint the color version of the icon, which may look unusual. \
                Devices running earlier Android versions will show the untinted color icon when no monochrome layer is provided.
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