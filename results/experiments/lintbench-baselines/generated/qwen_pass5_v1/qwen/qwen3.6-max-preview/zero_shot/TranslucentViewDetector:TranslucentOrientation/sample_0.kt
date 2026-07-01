package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
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
        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape", "userPortrait", "userLandscape", "locked"
        )

        val ISSUE = Issue.create(
            "TranslucentOrientation",
            "Mixing screenOrientation and translucency",
            "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with `targetSdkVersion` O or greater since there can be an another activity " +
                "visible behind your activity with a conflicting request.\n\n" +
                "For example, your activity requests landscape and the visible activity behind " +
                "your translucent activity request portrait. In this case the system can only " +
                "honor one of the requests and currently prefers to honor the request from " +
                "non-translucent activities since there is nothing visible behind them.\n\n" +
                "Devices running platform version O or greater will throw an exception in your " +
                "app if this state is detected.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(TranslucentViewDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }

    override fun getApplicableElements(): List<String>? = listOf(TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion ?: 0
        if (targetSdk < 26) return

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty() || !FIXED_ORIENTATIONS.contains(orientation)) return

        if (isTranslucentTheme(context, element)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Translucent activities with a fixed orientation are not supported on API 26+"
            )
        }
    }

    private fun isTranslucentTheme(context: XmlContext, activityElement: Element): Boolean {
        var themeAttr = activityElement.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (themeAttr.isEmpty()) {
            val manifest = activityElement.ownerDocument.documentElement
            val application = manifest.getElementsByTagName(TAG_APPLICATION).item(0) as? Element
            if (application != null) {
                themeAttr = application.getAttributeNS(ANDROID_URI, ATTR_THEME)
            }
        }
        if (themeAttr.isEmpty()) return false

        val resolver = context.resourceResolver ?: return false
        val theme = resolver.findResValue(themeAttr, false) ?: return false

        val isTranslucent = resolver.getBooleanValue(theme, "windowIsTranslucent", false)
        val isFloating = resolver.getBooleanValue(theme, "windowIsFloating", false)

        return isTranslucent == true || isFloating == true
    }
}