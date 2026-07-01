package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape",
            "locked", "nosensor"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with `targetSdkVersion` O or greater since there can be an another activity visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind your translucent activity request portrait. In this case the system can only honor one of the requests and currently prefers to honor the request from non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("activity")

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion ?: return
        if (targetSdk < 26) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation") ?: return
        if (orientationAttr.value !in FIXED_ORIENTATIONS) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme") ?: return
        if (!themeAttr.value.contains("Translucent", ignoreCase = true)) return

        context.report(
            ISSUE,
            context.getLocation(orientationAttr),
            "Fixed screen orientation is not supported with translucent themes on API 26+"
        )
    }
}