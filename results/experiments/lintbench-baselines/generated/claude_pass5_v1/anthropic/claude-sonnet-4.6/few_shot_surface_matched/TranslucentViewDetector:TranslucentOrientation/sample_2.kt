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

    // Track activity names that have a fixed screen orientation set in the manifest
    private val orientationActivities = mutableSetOf<String>()

    // Track activity names that have a translucent/floating theme set in the manifest
    private val translucentActivities = mutableSetOf<String>()

    companion object {
        private const val O_API_LEVEL = 26

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

        private val TRANSLUCENT_THEMES = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Dialog",
            "Theme.InputMethod",
            "Theme.Panel",
            "Theme.Light",
            "Theme.Holo.Dialog",
            "Theme.Holo.Light.Dialog",
            "Theme.Material.Dialog",
            "Theme.Material.Light.Dialog",
            "Theme.AppCompat.Dialog",
            "Theme.AppCompat.Light.Dialog",
            "android:style/Theme.Translucent",
            "android:style/Theme.Translucent.NoTitleBar",
            "android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "android:style/Theme.Dialog",
            "android:style/Theme.InputMethod",
            "android:style/Theme.Panel"
        )

        private const val SET_REQUESTED_ORIENTATION = "setRequestedOrientation"

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
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
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            androidSpecific = true
        )
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun getApplicableAttributes(): Collection<String> =
        listOf(ATTR_SCREEN_ORIENTATION, ATTR_THEME)

    override fun visitElement(context: XmlContext, element: Element) {
        // We handle everything in visitAttribute; this entry point is used to
        // detect the case where both attributes are present on the same element.
        if (element.localName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)

        if (orientationAttr == null || themeAttr == null) return

        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) return

        val theme = themeAttr.value
        if (!isTranslucentTheme(theme)) return

        val location = context.getValueLocation(orientationAttr)
        val secondaryLocation = context.getValueLocation(themeAttr)
            .withSecondary(context.getValueLocation(themeAttr), "Translucent theme defined here")
        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val message = buildMessage(activityName)

        val incident = Incident(ISSUE, element, location, message)
        context.report(incident, targetSdkAtLeast(O_API_LEVEL))
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        if (element.localName != TAG_ACTIVITY) return

        when (attribute.localName) {
            ATTR_SCREEN_ORIENTATION -> {
                val orientation = attribute.value
                if (!isFixedOrientation(orientation)) return

                // Check if there is also a translucent theme on the same element
                val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
                if (themeAttr != null && isTranslucentTheme(themeAttr.value)) {
                    // Will be reported in visitElement to avoid duplicates
                    return
                }

                // Record for cross-checking with programmatic setRequestedOrientation
                val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (activityName.isNotEmpty()) {
                    orientationActivities.add(activityName)
                }
            }

            ATTR_THEME -> {
                val theme = attribute.value
                if (!isTranslucentTheme(theme)) return

                // Check if there is also a fixed orientation on the same element
                val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr != null && isFixedOrientation(orientationAttr.value)) {
                    // Will be reported in visitElement to avoid duplicates
                    return
                }

                // Record for cross-checking with programmatic setRequestedOrientation
                val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (activityName.isNotEmpty()) {
                    translucentActivities.add(activityName)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(SET_REQUESTED_ORIENTATION)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity")) return

        val argument = node.valueArguments.firstOrNull() ?: return
        val argText = argument.asSourceString()

        // Check if the argument represents a fixed orientation constant
        if (!isFixedOrientationConstant(argText, context, node)) return

        // Try to determine the enclosing class name and match against translucent activities
        val containingClass = node.resolve()?.containingClass
        val enclosingClass = context.uastFile?.classes?.firstOrNull()
        val className = enclosingClass?.qualifiedName ?: return

        // Check if this class is known to be translucent from manifest analysis
        val isTranslucent = translucentActivities.any { it.endsWith(className) || className.endsWith(it.trimStart('.')) }

        if (!isTranslucent) return

        val message = buildMessage(className)
        val location = context.getLocation(node)
        val incident = Incident(ISSUE, node, location, message)
        context.report(incident, targetSdkAtLeast(O_API_LEVEL))
    }

    // -------------------------------------------------------------------------
    // filterIncident
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: com.android.tools.lint.detector.api.LintMap): Boolean {
        // Additional filtering can be applied here if needed.
        // Return true to suppress the incident, false to keep it.
        return false
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun isFixedOrientation(orientation: String): Boolean {
        return FIXED_ORIENTATIONS.any { orientation.equals(it, ignoreCase = true) }
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val stripped = theme
            .removePrefix("@android:style/")
            .removePrefix("@style/")
            .removePrefix("android:style/")
        return TRANSLUCENT_THEMES.any { reference ->
            val strippedRef = reference
                .removePrefix("@android:style/")
                .removePrefix("@style/")
                .removePrefix("android:style/")
            stripped.equals(strippedRef, ignoreCase = false) ||
                stripped.contains("Translucent", ignoreCase = true) ||
                stripped.contains("Dialog", ignoreCase = true) ||
                stripped.contains("Panel", ignoreCase = true) ||
                stripped.contains("InputMethod", ignoreCase = true)
        }
    }

    private fun isFixedOrientationConstant(
        argText: String,
        context: JavaContext,
        node: UCallExpression
    ): Boolean {
        // Check common ActivityInfo orientation constants
        return argText.contains("SCREEN_ORIENTATION_LANDSCAPE") ||
            argText.contains("SCREEN_ORIENTATION_PORTRAIT") ||
            argText.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE") ||
            argText.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT") ||
            argText.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE") ||
            argText.contains("SCREEN_ORIENTATION_SENSOR_PORTRAIT") ||
            argText.contains("SCREEN_ORIENTATION_USER_LANDSCAPE") ||
            argText.contains("SCREEN_ORIENTATION_USER_PORTRAIT") ||
            argText.contains("SCREEN_ORIENTATION_LOCKED")
    }

    private fun buildMessage(activityName: String): String {
        val name = if (activityName.isNotEmpty()) "`$activityName`" else "This activity"
        return "$name has a fixed screen orientation and a translucent theme. " +
            "This combination is not supported on apps with targetSdkVersion O or greater " +
            "and will throw an exception on devices running Android O or greater."
    }
}