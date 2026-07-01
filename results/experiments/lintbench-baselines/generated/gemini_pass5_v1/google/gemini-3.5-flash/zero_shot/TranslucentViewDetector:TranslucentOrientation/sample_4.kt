package com.android.tools.lint.checks

import com.android.tools.SdkConstants.ANDROID_URI
import com.android.tools.SdkConstants.TAG_ACTIVITY
import com.android.tools.SdkConstants.TAG_APPLICATION
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkVersion = context.project.targetSdkVersion.apiLevel
        if (targetSdkVersion < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation") ?: return
        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        val theme = getTheme(element) ?: return
        if (isTranslucentTheme(theme)) {
            val location = context.getLocation(orientationAttr)
            context.report(
                ISSUE,
                element,
                location,
                "Only fullscreen opaque activities can request orientation"
            )
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape",
            "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked" -> true
            else -> false
        }
    }

    private fun getTheme(element: Element): String? {
        if (element.hasAttributeNS(ANDROID_URI, "theme")) {
            return element.getAttributeNS(ANDROID_URI, "theme")
        }
        val parent = element.parentNode as? Element
        if (parent != null && parent.tagName == TAG_APPLICATION) {
            if (parent.hasAttributeNS(ANDROID_URI, "theme")) {
                return parent.getAttributeNS(ANDROID_URI, "theme")
            }
        }
        return null
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val name = theme.substringAfter('/')
        return name.contains("Translucent", ignoreCase = true) ||
                name.contains("Dialog", ignoreCase = true) ||
                name.contains("Floating", ignoreCase = true)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity requests portrait. In this case the system can only \
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
    }
}