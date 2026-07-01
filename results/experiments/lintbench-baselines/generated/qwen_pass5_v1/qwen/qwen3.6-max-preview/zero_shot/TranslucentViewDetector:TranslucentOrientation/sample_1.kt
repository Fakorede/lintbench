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
        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape"
        )

        @JvmField
        val ISSUE_TRANSLUCENT_ORIENTATION = Issue.create(
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
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_ACTIVITY_ALIAS)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion
        if (targetSdk != null && targetSdk < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
        if (orientationAttr == null) return

        val orientationValue = orientationAttr.value
        if (orientationValue !in FIXED_ORIENTATIONS) return

        var themeUrl = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)

        if (themeUrl.isEmpty()) {
            val manifest = element.ownerDocument.documentElement
            val appElements = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION)
            if (appElements.length > 0) {
                val appElement = appElements.item(0) as? Element
                themeUrl = appElement?.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME) ?: ""
            }
        }

        if (themeUrl.isNotEmpty() && isTranslucentTheme(context, themeUrl)) {
            context.report(
                ISSUE_TRANSLUCENT_ORIENTATION,
                orientationAttr,
                context.getLocation(orientationAttr),
                "Fixed screen orientation is not supported with translucent themes on API 26+"
            )
        }
    }

    private fun isTranslucentTheme(context: XmlContext, themeUrl: String): Boolean {
        if (themeUrl.contains("Translucent", ignoreCase = true)) return true

        val resolver = context.resourceResolver ?: return false

        val translucentValue = resolver.getResourceValue(themeUrl, "windowIsTranslucent", true)
        if (translucentValue?.value == "true") return true

        val floatingValue = resolver.getResourceValue(themeUrl, "windowIsFloating", true)
        if (floatingValue?.value == "true") return true

        val swipeDismissValue = resolver.getResourceValue(themeUrl, "windowSwipeToDismiss", true)
        if (swipeDismissValue?.value == "true") return true

        return false
    }
}