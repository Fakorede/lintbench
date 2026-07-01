package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_STYLE
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
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

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
            "Theme.Light.Panel",
            "android:Theme.Translucent",
            "android:Theme.Translucent.NoTitleBar",
            "android:Theme.Translucent.NoTitleBar.Fullscreen",
            "android:Theme.Dialog",
            "android:Theme.InputMethod",
            "android:Theme.Panel",
            "android:Theme.Light.Panel"
        )

        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"
        private const val KEY_LOCATION = "location"

        private const val EXPLANATION =
            "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with `targetSdkVersion` O or greater since there can be another activity " +
                "visible behind your activity with a conflicting request.\n\n" +
                "For example, your activity requests landscape and the visible activity behind " +
                "your translucent activity requests portrait. In this case the system can only " +
                "honor one of the requests and currently prefers to honor the request from " +
                "non-translucent activities since there is nothing visible behind them.\n\n" +
                "Devices running platform version O or greater will throw an exception in your " +
                "app if this state is detected."

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private fun isFixedOrientation(orientation: String): Boolean {
            return orientation in FIXED_ORIENTATIONS
        }

        private fun isTranslucentTheme(theme: String): Boolean {
            val stripped = theme.removePrefix("@style/").removePrefix("@android:style/")
            return TRANSLUCENT_THEMES.any { translucent ->
                stripped == translucent ||
                    stripped == translucent.removePrefix("android:") ||
                    theme == translucent ||
                    theme.endsWith("/$translucent")
            }
        }
    }

    // --- XmlScanner ---

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_SCREEN_ORIENTATION)

    override fun visitElement(context: XmlContext, element: Element) {
        // Check activity elements for both screenOrientation and theme attributes
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) return

        // Check for theme attribute
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme") ?: return
        val theme = themeAttr.value
        if (!isTranslucentTheme(theme)) return

        val location = context.getValueLocation(orientationAttr)
        val message =
            "Should not specify `screenOrientation` with a translucent theme " +
                "(`$theme`) on API O and greater: see issue explanation for details."
        val incident = Incident(ISSUE, element, location, message)
        context.report(incident, targetSdkAtLeast(O_API_LEVEL))
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // screenOrientation attribute visited — check parent element for translucent theme
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY && element.localName != TAG_ACTIVITY) return

        val orientation = attribute.value
        if (!isFixedOrientation(orientation)) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme") ?: return
        val theme = themeAttr.value
        if (!isTranslucentTheme(theme)) return

        // Already handled in visitElement, avoid double-reporting
    }

    // --- SourceCodeScanner ---

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setRequestedOrientation",
        "setTranslucent"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        if (!evaluator.extendsClass(containingClass, "android.app.Activity", true) &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Activity")
        ) {
            return
        }

        when (methodName) {
            "setRequestedOrientation" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                val argText = arg.asSourceString()
                // Check for fixed orientation constants
                val isFixed = argText.contains("SCREEN_ORIENTATION_LANDSCAPE") ||
                    argText.contains("SCREEN_ORIENTATION_PORTRAIT") ||
                    argText.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE") ||
                    argText.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT") ||
                    argText.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE") ||
                    argText.contains("SCREEN_ORIENTATION_SENSOR_PORTRAIT") ||
                    argText.contains("SCREEN_ORIENTATION_USER_LANDSCAPE") ||
                    argText.contains("SCREEN_ORIENTATION_USER_PORTRAIT") ||
                    argText.contains("SCREEN_ORIENTATION_LOCKED")

                if (!isFixed) return

                val location = context.getLocation(node)
                val message =
                    "Should not specify a fixed `screenOrientation` when a translucent theme " +
                        "may be in use on API O and greater: see issue explanation for details."
                val incident = Incident(ISSUE, node, location, message)
                context.report(incident, targetSdkAtLeast(O_API_LEVEL))
            }

            "setTranslucent" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = arg.asSourceString()
                if (value != "true") return

                val location = context.getLocation(node)
                val message =
                    "Should not make activity translucent when a fixed `screenOrientation` " +
                        "may be set on API O and greater: see issue explanation for details."
                val incident = Incident(ISSUE, node, location, message)
                context.report(incident, targetSdkAtLeast(O_API_LEVEL))
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report if targetSdkVersion >= O (26)
        val project = context.project
        val targetSdk = project.targetSdk
        return targetSdk >= O_API_LEVEL
    }
}