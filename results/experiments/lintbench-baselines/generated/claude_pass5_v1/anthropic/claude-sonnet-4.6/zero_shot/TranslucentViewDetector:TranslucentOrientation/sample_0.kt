package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFixer
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Detector that checks for activities that combine a fixed screen orientation
 * with a translucent theme, which is not supported on Android O (API 26) and higher.
 */
class TranslucentViewDetector : ResourceXmlDetector(), XmlScanner {

    companion object {
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"

        // Orientation values that fix the screen orientation
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

        // Theme attributes/names that indicate translucency
        private val TRANSLUCENT_THEMES = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Dialog",
            "Theme.InputMethod",
            "Theme.Panel",
            "Theme.Light",
            "android:Theme.Translucent",
            "android:Theme.Translucent.NoTitleBar",
            "android:Theme.Translucent.NoTitleBar.Fullscreen",
            "android:Theme.Dialog",
            "android:Theme.InputMethod",
            "android:Theme.Panel",
            "android:Theme.Light"
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
                Scope.MANIFEST_SCOPE
            )
        )

        private fun isFixedOrientation(orientation: String): Boolean {
            return FIXED_ORIENTATIONS.contains(orientation)
        }

        private fun isTranslucentTheme(theme: String): Boolean {
            val cleanTheme = theme.removePrefix("@style/").removePrefix("@android:style/")
            return TRANSLUCENT_THEMES.any { translucentTheme ->
                cleanTheme == translucentTheme ||
                        cleanTheme.contains("Translucent", ignoreCase = true) ||
                        cleanTheme.contains("Dialog", ignoreCase = true) ||
                        cleanTheme.contains("Floating", ignoreCase = true)
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check if targetSdkVersion >= 26 (O)
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < 26) {
            return
        }

        // Get the screenOrientation attribute
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return

        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        // Get the theme attribute
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
        if (themeAttr != null) {
            val theme = themeAttr.value
            if (isTranslucentTheme(theme)) {
                reportIssue(context, element, orientationAttr.ownerElement, orientation, theme)
                return
            }
        }

        // Also check if the activity name references a known translucent style
        // by looking at the manifest application theme as fallback
        val applicationElement = element.parentNode
        if (applicationElement != null && applicationElement.nodeType == Node.ELEMENT_NODE) {
            val appElement = applicationElement as Element
            val appThemeAttr = appElement.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
            if (appThemeAttr != null) {
                val appTheme = appThemeAttr.value
                if (isTranslucentTheme(appTheme)) {
                    reportIssue(context, element, orientationAttr.ownerElement, orientation, appTheme)
                }
            }
        }
    }

    private fun reportIssue(
        context: XmlContext,
        element: Element,
        ownerElement: Element,
        orientation: String,
        theme: String
    ) {
        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val message = if (activityName.isNotEmpty()) {
            "The activity `$activityName` has a fixed orientation (`$orientation`) but also " +
                    "uses a translucent theme (`$theme`). This combination is not supported " +
                    "on Android O and later. You should either remove the orientation " +
                    "restriction or make the activity non-translucent."
        } else {
            "This activity has a fixed orientation (`$orientation`) but also uses a " +
                    "translucent theme (`$theme`). This combination is not supported on " +
                    "Android O and later. You should either remove the orientation " +
                    "restriction or make the activity non-translucent."
        }

        val orientationAttrNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val location = if (orientationAttrNode != null) {
            context.getLocation(orientationAttrNode)
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