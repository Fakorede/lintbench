package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFixer
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val USES_SDK_TAG = "uses-sdk"

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

        private val TRANSLUCENT_THEME_PATTERNS = listOf(
            "translucent",
            "Translucent",
            "transparent",
            "Transparent",
            "floating",
            "Floating",
            "dialog",
            "Dialog"
        )

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
                EnumSet.of(Scope.MANIFEST)
            )
        )

        private const val O_TARGET_SDK = 26
    }

    private var targetSdkVersion: Int = 1
    private var applicationTheme: String? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, USES_SDK_TAG)
    }

    override fun beforeCheckFile(context: Context) {
        targetSdkVersion = 1
        applicationTheme = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            USES_SDK_TAG -> {
                val targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                if (targetSdk.isNotEmpty()) {
                    targetSdkVersion = targetSdk.toIntOrNull() ?: 1
                }
            }
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            TAG_ACTIVITY -> {
                checkActivity(context, element)
            }
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        if (targetSdkVersion < O_TARGET_SDK) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return

        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        val activityTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        val effectiveTheme = if (activityTheme.isNotEmpty()) activityTheme else applicationTheme

        if (effectiveTheme != null && isTranslucentTheme(effectiveTheme)) {
            val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val message = buildString {
                append("Mixing screenOrientation with a translucent theme is not allowed for ")
                if (activityName.isNotEmpty()) {
                    append("`$activityName`")
                } else {
                    append("this activity")
                }
                append("; this combination is not supported on apps with `targetSdkVersion` O")
                append(" or greater (current target is `$targetSdkVersion`)")
            }

            val location: Location = if (activityTheme.isNotEmpty()) {
                val themeAttrNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
                if (themeAttrNode != null) {
                    context.getValueLocation(themeAttrNode)
                } else {
                    context.getLocation(element)
                }
            } else {
                context.getLocation(element)
            }

            context.report(
                issue = ISSUE,
                scope = element,
                location = location,
                message = message
            )
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        val normalized = orientation.removePrefix("@android:integer/")
            .removePrefix("@integer/")
        return FIXED_ORIENTATIONS.any { it.equals(normalized, ignoreCase = true) }
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        return TRANSLUCENT_THEME_PATTERNS.any { pattern ->
            theme.contains(pattern, ignoreCase = false)
        }
    }
}