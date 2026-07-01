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
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
        val theme = if (themeAttr != null) {
            themeAttr.value
        } else {
            val appElement = element.parentNode as? Element
            if (appElement != null && appElement.tagName == TAG_APPLICATION) {
                appElement.getAttributeNS(ANDROID_URI, ATTR_THEME)
            } else {
                ""
            }
        }

        if (theme.isNotEmpty() && isTranslucentTheme(theme)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(orientationAttr),
                "Should not specify screen orientation with a translucent theme when targeting O or higher"
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

    private fun isTranslucentTheme(theme: String): Boolean {
        val lower = theme.lowercase()
        return lower.contains("translucent") ||
                lower.contains("dialog") ||
                lower.contains("floating")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }
}