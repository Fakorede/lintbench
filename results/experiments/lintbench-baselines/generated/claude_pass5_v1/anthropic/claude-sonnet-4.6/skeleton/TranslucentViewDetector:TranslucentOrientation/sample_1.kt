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
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
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
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"
        private const val KEY_LOCATION = "location"

        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked",
        )

        private val TRANSLUCENT_THEMES = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Dialog",
            "Theme.InputMethod",
            "Theme.Panel",
            "Theme.Light",
            "Theme.Light.NoTitleBar",
            "Theme.Light.NoTitleBar.Fullscreen",
            "Theme.Holo.Dialog",
            "Theme.Holo.Light.Dialog",
            "Theme.AppCompat.Dialog",
            "Theme.AppCompat.Light.Dialog",
            "Theme.MaterialComponents.Dialog",
            "Theme.Material.Dialog",
        )

        private fun isTranslucentTheme(theme: String): Boolean {
            val themeName = theme.removePrefix("@android:style/")
                .removePrefix("@style/")
                .removePrefix("android:style/")
            return TRANSLUCENT_THEMES.any { translucent ->
                themeName == translucent || themeName.contains("Translucent") ||
                    themeName.contains("Dialog") || themeName.contains("Floating") ||
                    themeName.contains("Panel") || themeName.contains("translucent") ||
                    themeName.contains("dialog") || themeName.contains("floating")
            }
        }

        private fun isFixedOrientation(orientation: String): Boolean {
            return orientation in FIXED_ORIENTATIONS
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML || folderType == ResourceFolderType.LAYOUT ||
            folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_SCREEN_ORIENTATION, ATTR_THEME)
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Handled in visitElement
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value ?: return

        if (!isFixedOrientation(orientation)) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
        val theme = themeAttr?.value ?: return

        if (!isTranslucentTheme(theme)) return

        val message = "Combining a translucent theme with a fixed screen orientation on " +
            "SDK version O or greater can cause an exception to be thrown"

        val incident = Incident(ISSUE, element, context.getElementLocation(element), message)
        context.report(incident, map().put(KEY_ORIENTATION, orientation).put(KEY_THEME, theme))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        // Only flag if targetSdkVersion >= 26 (O)
        return targetSdk >= 26
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check if the activity is using a translucent theme
        // This is difficult to determine statically for method calls,
        // so we report a potential issue when setRequestedOrientation is called
        // with a fixed orientation value

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val arg = arguments[0]
        val evaluator = context.evaluator

        // Try to resolve the constant value
        val value = arg.evaluate()
        if (value is Int) {
            // ActivityInfo.SCREEN_ORIENTATION_* constants
            // Fixed orientation values that are problematic:
            // SCREEN_ORIENTATION_LANDSCAPE = 0
            // SCREEN_ORIENTATION_PORTRAIT = 1
            // SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
            // SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
            // SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6
            // SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7
            // SCREEN_ORIENTATION_USER_LANDSCAPE = 11
            // SCREEN_ORIENTATION_USER_PORTRAIT = 12
            // SCREEN_ORIENTATION_LOCKED = 14
            val fixedOrientations = setOf(0, 1, 6, 7, 8, 9, 11, 12, 14)
            if (value !in fixedOrientations) return
        } else {
            // Could not evaluate, skip
            return
        }

        val message = "Calling `setRequestedOrientation` in a translucent activity on " +
            "SDK version O or greater can cause an exception to be thrown"

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        context.report(incident, map())
    }
}