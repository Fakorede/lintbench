package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with targetSdkVersion O or greater since there can be another activity visible " +
                "behind your activity with a conflicting request. Devices running platform version O or " +
                "greater will throw an exception in your app if this state is detected.",
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
            "userPortrait", "userLandscape",
            "locked", "nosensor"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.TAG_ACTIVITY,
        SdkConstants.TAG_ACTIVITY_ALIAS
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion ?: return
        if (targetSdk < 26) return

        val orientationAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
        if (orientationAttr == null || !FIXED_ORIENTATIONS.contains(orientationAttr.value)) return

        var themeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
        if (themeAttr == null) {
            val parent = element.parentNode
            if (parent is Element && parent.tagName == SdkConstants.TAG_APPLICATION) {
                themeAttr = parent.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
            }
        }
        if (themeAttr == null) return

        if (isTranslucentTheme(themeAttr.value)) {
            context.report(
                ISSUE,
                context.getLocation(orientationAttr),
                "Fixed screen orientation is not supported with translucent themes on API 26+"
            )
        }
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        return theme.contains("Translucent", ignoreCase = true)
    }
}