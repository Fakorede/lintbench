package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    // Tracks activities that have a fixed orientation set in the manifest
    // Maps activity name -> orientation value
    private val activitiesWithOrientation = mutableMapOf<String, String>()

    // Tracks activities that have a translucent theme set in the manifest
    // Maps activity name -> theme value
    private val activitiesWithTranslucentTheme = mutableMapOf<String, String>()

    // Fixed orientation values that are not "unspecified" or "behind"
    private val fixedOrientations = setOf(
        "landscape",
        "portrait",
        "reverseLandscape",
        "reversePortrait",
        "sensorLandscape",
        "sensorPortrait",
        "userLandscape",
        "userPortrait",
        "sensor",
        "fullSensor",
        "nosensor",
        "locked",
        "user",
        "fullUser"
    )

    // Translucent-related theme name fragments
    private val translucentThemeFragments = listOf(
        "Translucent",
        "translucent",
        "Transparent",
        "transparent",
        "Dialog",
        "dialog",
        "Float",
        "float"
    )

    companion object {
        private const val TARGET_O = 26
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"

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
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            androidSpecific = true
        )

        private val SET_REQUESTED_ORIENTATION = "setRequestedOrientation"
        private val SET_TRANSLUCENT = "convertToTranslucent"
        private val ACTIVITY_CLASS = "android.app.Activity"

        private val ORIENTATION_CONSTANTS = mapOf(
            0 to "landscape",       // SCREEN_ORIENTATION_LANDSCAPE
            1 to "portrait",        // SCREEN_ORIENTATION_PORTRAIT
            2 to "user",            // SCREEN_ORIENTATION_USER
            4 to "sensor",          // SCREEN_ORIENTATION_SENSOR
            5 to "nosensor",        // SCREEN_ORIENTATION_NOSENSOR
            6 to "sensorLandscape", // SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            7 to "sensorPortrait",  // SCREEN_ORIENTATION_SENSOR_PORTRAIT
            8 to "reverseLandscape",// SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            9 to "reversePortrait", // SCREEN_ORIENTATION_REVERSE_PORTRAIT
            10 to "fullSensor",     // SCREEN_ORIENTATION_FULL_SENSOR
            11 to "userLandscape",  // SCREEN_ORIENTATION_USER_LANDSCAPE
            12 to "userPortrait",   // SCREEN_ORIENTATION_USER_PORTRAIT
            13 to "fullUser",       // SCREEN_ORIENTATION_FULL_USER
            14 to "locked"          // SCREEN_ORIENTATION_LOCKED
        )
    }

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun getApplicableAttributes(): Collection<String> =
        listOf(ATTR_SCREEN_ORIENTATION, ATTR_THEME)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (activityName.isBlank()) return

        when (attribute.localName) {
            ATTR_SCREEN_ORIENTATION -> {
                val orientation = attribute.value ?: return
                if (isFixedOrientation(orientation)) {
                    activitiesWithOrientation[activityName] = orientation
                }
            }
            ATTR_THEME -> {
                val theme = attribute.value ?: return
                if (isTranslucentTheme(theme)) {
                    activitiesWithTranslucentTheme[activityName] = theme
                }
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (activityName.isBlank()) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)

        val hasFixedOrientation = orientationAttr != null && isFixedOrientation(orientationAttr.value)
        val hasTranslucentTheme = themeAttr != null && isTranslucentTheme(themeAttr.value)

        if (hasFixedOrientation && hasTranslucentTheme) {
            val location = context.getLocation(orientationAttr ?: element)
            val message = buildMessage(orientationAttr?.value ?: "", themeAttr?.value ?: "")
            val incident = Incident(ISSUE, element, location, message)
            context.report(incident, targetSdkAtLeast(TARGET_O))
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> =
        listOf(SET_REQUESTED_ORIENTATION, SET_TRANSLUCENT)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, ACTIVITY_CLASS)) return

        when (method.name) {
            SET_REQUESTED_ORIENTATION -> {
                // Check if the argument is a fixed orientation constant
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = arg.evaluate()
                if (value is Int && isFixedOrientationConstant(value)) {
                    val location = context.getLocation(node)
                    val message = "Setting a fixed `screenOrientation` on a translucent activity " +
                        "is not supported on API 26+; this will throw an exception"
                    val incident = Incident(ISSUE, node, location, message)
                    context.report(incident, map().also { it.put(KEY_ORIENTATION, value.toString()) }, targetSdkAtLeast(TARGET_O))
                }
            }
            SET_TRANSLUCENT -> {
                val location = context.getLocation(node)
                val message = "Calling `convertToTranslucent` on an activity with a fixed " +
                    "`screenOrientation` is not supported on API 26+; this will throw an exception"
                val incident = Incident(ISSUE, node, location, message)
                context.report(incident, map().also { it.put(KEY_THEME, "translucent") }, targetSdkAtLeast(TARGET_O))
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Additional filtering logic if needed based on stored map data
        // By default, allow all incidents through
        return true
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun isFixedOrientation(orientation: String): Boolean {
        // Strip any namespace prefix
        val value = orientation.substringAfterLast('/')
        return fixedOrientations.contains(value)
    }

    private fun isFixedOrientationConstant(value: Int): Boolean {
        return ORIENTATION_CONSTANTS.containsKey(value)
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        return translucentThemeFragments.any { fragment -> theme.contains(fragment) }
    }

    private fun buildMessage(orientation: String, theme: String): String {
        return "Mixing a fixed `screenOrientation` (\"$orientation\") with a translucent " +
            "theme (\"$theme\") is not supported on API 26+; this combination will throw " +
            "an exception on devices running Android O or greater"
    }
}