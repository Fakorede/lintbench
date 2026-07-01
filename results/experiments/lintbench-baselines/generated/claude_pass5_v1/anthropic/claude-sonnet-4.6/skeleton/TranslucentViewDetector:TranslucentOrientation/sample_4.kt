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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
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
        private const val KEY_ACTIVITY = "activity"
        private const val KEY_THEME = "theme"
        private const val MIN_SDK = 26 // Android O

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
            "@android:style/Theme.Translucent",
            "@android:style/Theme.Translucent.NoTitleBar",
            "@android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@android:style/Theme.Dialog",
            "@style/Theme.AppCompat.Dialog",
            "@style/Theme.AppCompat.Light.Dialog",
            "@style/Theme.MaterialComponents.Dialog",
            "@style/Theme.MaterialComponents.Light.Dialog",
        )

        private fun isTranslucentTheme(theme: String): Boolean {
            return TRANSLUCENT_THEMES.any { theme.equals(it, ignoreCase = true) } ||
                theme.contains("Translucent", ignoreCase = true) ||
                theme.contains("Dialog", ignoreCase = true) ||
                theme.contains("Floating", ignoreCase = true)
        }

        private fun isFixedOrientation(orientation: String): Boolean {
            return FIXED_ORIENTATIONS.any { it.equals(orientation, ignoreCase = true) }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML || folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_SCREEN_ORIENTATION)
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != ATTR_SCREEN_ORIENTATION) return
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        val orientationValue = attribute.value ?: return
        if (!isFixedOrientation(orientationValue)) return

        val themeAttr = element.getAttributeNS(ANDROID_URI, ATTR_THEME) ?: return
        if (themeAttr.isBlank()) return

        if (isTranslucentTheme(themeAttr)) {
            val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
            val location = context.getLocation(attribute)
            val incident = Incident(
                ISSUE,
                attribute,
                location,
                "Mixing a fixed `screenOrientation` with a translucent theme is not allowed, " +
                    "and can lead to a crash on API 26+",
            )
            val map = LintMap()
            map.put(KEY_ORIENTATION, orientationValue)
            map.put(KEY_ACTIVITY, activityName)
            map.put(KEY_THEME, themeAttr)
            context.report(incident, map)
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientationAttr != null) {
            // Will be handled by visitAttribute
            return
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        return targetSdk >= MIN_SDK
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (!qualifiedName.contains("Activity") && qualifiedName != "android.app.Activity") return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val orientationArg = args[0]
        val orientationText = orientationArg.asSourceString()

        val isFixed = orientationText.contains("SCREEN_ORIENTATION_LANDSCAPE", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_PORTRAIT", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_SENSOR_PORTRAIT", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_USER_LANDSCAPE", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_USER_PORTRAIT", ignoreCase = true) ||
            orientationText.contains("SCREEN_ORIENTATION_LOCKED", ignoreCase = true)

        if (!isFixed) return

        val location = context.getLocation(node)
        val incident = Incident(
            ISSUE,
            node,
            location,
            "Mixing a fixed `screenOrientation` with a translucent theme is not allowed, " +
                "and can lead to a crash on API 26+",
        )
        val map = LintMap()
        map.put(KEY_ORIENTATION, orientationText)
        context.report(incident, map)
    }
}