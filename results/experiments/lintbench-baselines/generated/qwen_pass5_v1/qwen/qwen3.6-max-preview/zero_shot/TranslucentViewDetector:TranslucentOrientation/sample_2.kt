package com.android.tools.lint.checks

import com.android.SdkConstants
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
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported  on apps with `targetSdkVersion` O or greater since there can be an another activity  visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind  your translucent activity request portrait. In this case the system can only  honor one of the requests and currently prefers to honor the request from  non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your  app if this state is detected.
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
            "portrait", "landscape", "reversePortrait", "reverseLandscape"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.TAG_ACTIVITY,
        SdkConstants.TAG_ACTIVITY_ALIAS
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion ?: return
        if (targetSdk < 26) return

        val orientationAttr = element.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.value
        if (orientation !in FIXED_ORIENTATIONS) return

        val themeAttr = element.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME) ?: return
        val theme = themeAttr.value
        if (!theme.contains("Translucent", ignoreCase = true)) return

        context.report(
            ISSUE,
            context.getLocation(orientationAttr),
            "Translucent activities with `targetSdkVersion` O or higher cannot specify a fixed `screenOrientation`."
        )
    }
}