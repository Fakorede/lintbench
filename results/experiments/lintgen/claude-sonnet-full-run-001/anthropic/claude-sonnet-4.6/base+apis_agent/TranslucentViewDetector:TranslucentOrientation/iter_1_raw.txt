package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity requests portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"

        // Orientation values that are considered "fixed" (not unspecified/sensor/etc.)
        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked"
        )

        // Theme name patterns that indicate translucency
        private val TRANSLUCENT_THEME_PATTERNS = listOf(
            "Translucent",
            "translucent",
            "Transparent",
            "transparent",
            "Dialog",
            "dialog",
            "Float",
            "float"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Only relevant for targetSdkVersion >= 26 (O)
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < 26) {
            return
        }

        // Check if screenOrientation is set to a fixed value
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return

        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        // Check if the activity has a translucent theme
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
            ?: return

        val theme = themeAttr.value
        if (!isTranslucentTheme(theme)) {
            return
        }

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val message = buildString {
            append("Mixing screenOrientation with a translucent theme is not supported on API 26+")
            if (activityName.isNotEmpty()) {
                append(" (activity `$activityName`)")
            }
            append(". This will throw an exception on devices running API 26 or greater.")
        }

        val location = context.getLocation(element)
        context.report(
            issue = ISSUE,
            scope = element,
            location = location,
            message = message
        )
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        if (orientation.startsWith("@") || orientation.startsWith("?")) {
            return false
        }
        return FIXED_ORIENTATIONS.contains(orientation)
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val themeName = extractThemeName(theme) ?: theme
        return containsTranslucentPattern(themeName)
    }

    private fun extractThemeName(themeRef: String): String? {
        // e.g. "@style/Theme.MyApp.Translucent" -> "Theme.MyApp.Translucent"
        // e.g. "@android:style/Theme.Translucent" -> "Theme.Translucent"
        val slashIndex = themeRef.lastIndexOf('/')
        return if (slashIndex >= 0) {
            themeRef.substring(slashIndex + 1)
        } else {
            val questionIndex = themeRef.indexOf('?')
            val colonIndex = themeRef.indexOf(':')
            when {
                questionIndex >= 0 && colonIndex >= 0 -> themeRef.substring(colonIndex + 1)
                questionIndex >= 0 -> themeRef.substring(questionIndex + 1)
                else -> themeRef
            }
        }
    }

    private fun containsTranslucentPattern(name: String): Boolean {
        return TRANSLUCENT_THEME_PATTERNS.any { pattern -> name.contains(pattern) }
    }
}