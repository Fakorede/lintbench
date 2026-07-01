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
import com.android.tools.lint.detector.api.Location
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

    companion object {
        private const val O_API_LEVEL = 26

        private val FIXED_ORIENTATION_VALUES = setOf(
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

        private val TRANSLUCENT_THEME_SUBSTRINGS = listOf(
            "Translucent",
            "translucent",
            "Transparent",
            "transparent",
            "Float",
            "float"
        )

        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"
        private const val KEY_LOCATION = "location"
        private const val KEY_ACTIVITY = "activity"

        private const val REQUEST_ORIENTATION_METHOD = "setRequestedOrientation"

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
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )

        private fun isFixedOrientation(orientation: String): Boolean {
            return orientation in FIXED_ORIENTATION_VALUES
        }

        private fun isTranslucentTheme(theme: String): Boolean {
            return TRANSLUCENT_THEME_SUBSTRINGS.any { theme.contains(it) }
        }
    }

    // -----------------------------------------------------------------------
    // XmlScanner – manifest checks
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun getApplicableAttributes(): Collection<String> = listOf(
        ATTR_SCREEN_ORIENTATION,
        ATTR_THEME
    )

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)

        if (orientationAttr == null || themeAttr == null) return

        val orientation = orientationAttr.value ?: return
        val theme = themeAttr.value ?: return

        if (!isFixedOrientation(orientation)) return
        if (!isTranslucentTheme(theme)) return

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val location = context.getValueLocation(orientationAttr)
        val message = buildMessage(activityName, orientation)

        val incident = Incident(ISSUE, element, location, message)
        context.report(incident, targetSdkAtLeast(O_API_LEVEL))
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Individual attribute visits are handled in visitElement where we can
        // check both orientation and theme together. No additional logic needed here.
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – Java/Kotlin source checks
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(REQUEST_ORIENTATION_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        if (!evaluator.extendsClass(containingClass, "android.app.Activity", true) &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Activity")
        ) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return

        // Try to resolve the orientation constant value
        val orientationValue = resolveOrientationConstant(argument) ?: return

        if (!isFixedOrientationInt(orientationValue)) return

        val location = context.getLocation(node)
        val message =
            "Setting a fixed screen orientation with `setRequestedOrientation` while using a " +
                "translucent theme is not supported on Android O and higher and will cause an " +
                "exception to be thrown."

        val incident = Incident(ISSUE, node, location, message)
        context.report(incident, targetSdkAtLeast(O_API_LEVEL))
    }

    private fun resolveOrientationConstant(expression: org.jetbrains.uast.UExpression): Int? {
        val evaluated = expression.evaluate()
        return when (evaluated) {
            is Int -> evaluated
            is Long -> evaluated.toInt()
            else -> null
        }
    }

    private fun isFixedOrientationInt(value: Int): Boolean {
        // ActivityInfo orientation constants for fixed orientations:
        // SCREEN_ORIENTATION_LANDSCAPE = 0
        // SCREEN_ORIENTATION_PORTRAIT = 1
        // SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
        // SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
        // SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6
        // SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7
        // SCREEN_ORIENTATION_USER_LANDSCAPE = 11
        // SCREEN_ORIENTATION_USER_PORTRAIT = 12
        // SCREEN_ORIENTATION_LOCKED = 14
        return value in setOf(0, 1, 6, 7, 8, 9, 11, 12, 14)
    }

    // -----------------------------------------------------------------------
    // filterIncident – conditional reporting
    // -----------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // The targetSdkAtLeast constraint handles the filtering via the report call;
        // here we can do additional checks if needed.
        if (context.mainProject.targetSdk < O_API_LEVEL) {
            return false
        }
        return true
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildMessage(activityName: String, orientation: String): String {
        val activityPart = if (activityName.isNotEmpty()) " for activity `$activityName`" else ""
        return "Specifying `screenOrientation=\"$orientation\"`$activityPart with a translucent " +
            "theme is not supported on Android O and higher and will cause an exception to be thrown."
    }
}