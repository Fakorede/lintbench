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
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
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
                Scope.MANIFEST_AND_RESOURCE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_IS_FLOATING = "windowIsFloating"

        // Orientation values that are "fixed" (not sensor/unspecified/etc.)
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

        // Style attributes that indicate translucency
        private val TRANSLUCENT_THEMES = setOf(
            "@android:style/Theme.Translucent",
            "@android:style/Theme.Translucent.NoTitleBar",
            "@android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@android:style/Theme.Dialog",
            "@android:style/Theme.Holo.Dialog",
            "@android:style/Theme.Holo.Light.Dialog",
            "@android:style/Theme.AppCompat.Dialog",
            "@android:style/Theme.AppCompat.Light.Dialog",
            "@style/Theme.AppCompat.Dialog",
            "@style/Theme.AppCompat.Light.Dialog"
        )

        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_ORIENTATION_LOCATION = "orientationLocation"
        private const val KEY_THEME = "theme"
        private const val KEY_ACTIVITY_NAME = "activityName"
    }

    // Map from activity name -> orientation value
    private val activityOrientations = mutableMapOf<String, String>()

    // Map from activity name -> orientation location
    private val activityOrientationLocations = mutableMapOf<String, Location>()

    // Map from activity name -> theme reference
    private val activityThemes = mutableMapOf<String, String>()

    // Application-level theme
    private var applicationTheme: String? = null

    // Map from theme name -> whether it is translucent (resolved from style resources)
    private val themeTranslucency = mutableMapOf<String, Boolean>()

    // Pending reports: activity name -> pair of (orientation location, theme)
    private val pendingReports = mutableListOf<PendingReport>()

    private data class PendingReport(
        val activityName: String,
        val orientationValue: String,
        val orientationLocation: Location,
        val theme: String
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> visitApplication(context, element)
            TAG_ACTIVITY -> visitActivity(context, element)
            "style" -> visitStyle(context, element)
        }
    }

    private fun visitApplication(context: XmlContext, element: Element) {
        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (theme.isNotEmpty()) {
            applicationTheme = theme
        }
    }

    private fun visitActivity(context: XmlContext, element: Element) {
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty() || orientation !in FIXED_ORIENTATIONS) {
            return
        }

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).ifEmpty {
            applicationTheme ?: return
        }

        val orientationLocation = context.getValueLocation(
            element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        )

        activityOrientations[activityName] = orientation
        activityOrientationLocations[activityName] = orientationLocation
        activityThemes[activityName] = theme

        // Check if the theme is obviously translucent by name
        if (isObviouslyTranslucentTheme(theme)) {
            reportIssue(context, orientationLocation, orientation, theme, activityName)
        } else {
            // Defer to after all resources are processed
            pendingReports.add(
                PendingReport(activityName, orientation, orientationLocation, theme)
            )
        }
    }

    private fun visitStyle(context: XmlContext, element: Element) {
        val styleName = element.getAttribute(ATTR_NAME)
        if (styleName.isEmpty()) return

        var isTranslucent = false
        var isFloating = false

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "item") {
                val itemName = child.getAttribute(ATTR_NAME)
                val itemValue = child.textContent?.trim() ?: ""
                when {
                    itemName == ATTR_WINDOW_IS_TRANSLUCENT && itemValue == "true" -> isTranslucent = true
                    itemName == ATTR_WINDOW_IS_FLOATING && itemValue == "true" -> isFloating = true
                }
            }
        }

        if (isTranslucent || isFloating) {
            themeTranslucency[styleName] = true
            themeTranslucency["@style/$styleName"] = true
        }

        // Check parent theme
        val parent = element.getAttribute("parent")
        if (parent.isNotEmpty() && isObviouslyTranslucentTheme(parent)) {
            themeTranslucency[styleName] = true
            themeTranslucency["@style/$styleName"] = true
        }
    }

    private fun isObviouslyTranslucentTheme(theme: String): Boolean {
        if (theme in TRANSLUCENT_THEMES) return true
        val lower = theme.lowercase()
        return lower.contains("translucent") || lower.contains("dialog") || lower.contains("floating")
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        if (isObviouslyTranslucentTheme(theme)) return true
        // Check resolved styles
        if (themeTranslucency[theme] == true) return true
        // Strip @style/ prefix and check
        val stripped = theme.removePrefix("@style/").removePrefix("@android:style/")
        return themeTranslucency[stripped] == true
    }

    private fun reportIssue(
        context: XmlContext,
        location: Location,
        orientation: String,
        theme: String,
        activityName: String
    ) {
        context.report(
            ISSUE,
            location,
            "Combining a fixed orientation (currently `$orientation`) with a translucent " +
                "theme (`$theme`) in activity `$activityName` is not allowed, and can cause " +
                "a crash on devices running API 26 and higher"
        )
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        // Process pending reports now that we may have style info from this file
        val iterator = pendingReports.iterator()
        while (iterator.hasNext()) {
            val report = iterator.next()
            if (isTranslucentTheme(report.theme)) {
                reportIssue(
                    context,
                    report.orientationLocation,
                    report.orientationValue,
                    report.theme,
                    report.activityName
                )
                iterator.remove()
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Any remaining pending reports where we couldn't resolve the theme
        // — skip them to avoid false positives
        pendingReports.clear()
    }
}