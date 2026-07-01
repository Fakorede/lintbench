package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
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
        @JvmField
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
            "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape",
            "userPortrait", "userLandscape"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion
        if (targetSdk != -1 && targetSdk < 26) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.nodeValue
        if (orientation !in FIXED_ORIENTATIONS) return

        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (theme.isEmpty()) return

        val isTranslucent = theme.contains("Translucent", ignoreCase = true)
        if (!isTranslucent) return

        context.report(
            ISSUE,
            context.getLocation(orientationAttr),
            "Translucent activities with a fixed orientation are not supported on API 26+ and will throw an exception."
        )
    }
}