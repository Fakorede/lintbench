package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape",
            "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("activity", "activity-alias")

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk < 26) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation") ?: return
        val orientation = orientationAttr.value
        if (orientation !in FIXED_ORIENTATIONS) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme")
        val themeValue = themeAttr?.value ?: ""
        if (!isTranslucentTheme(themeValue)) return

        context.report(
            ISSUE,
            orientationAttr,
            context.getLocation(orientationAttr),
            "Fixed screen orientation is not supported with translucent themes when targeting API 26+"
        )
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        return theme.contains("Translucent", ignoreCase = true)
    }
}