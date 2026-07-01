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
        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_IS_FLOATING = "windowIsFloating"

        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"
        private const val KEY_ACTIVITY = "activity"

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

        private val SET_ORIENTATION_METHODS = listOf(
            "setRequestedOrientation",
        )

        private const val MIN_SDK_WITH_ISSUE = 26 // O = API 26
    }

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_SCREEN_ORIENTATION)

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != ATTR_SCREEN_ORIENTATION) return
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        val orientationValue = attribute.value ?: return
        if (!isFixedOrientation(orientationValue)) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME) ?: return
        val themeValue = themeAttr.value ?: return

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""

        val location = context.getLocation(attribute)
        val incident = Incident(
            ISSUE,
            attribute,
            location,
            "Should not specify screen orientation with translucent or floating theme",
        )

        val map = LintMap()
        map.put(KEY_ORIENTATION, orientationValue)
        map.put(KEY_THEME, themeValue)
        map.put(KEY_ACTIVITY, activityName)

        context.report(incident, map)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Handled via visitAttribute
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        if (targetSdk < MIN_SDK_WITH_ISSUE) {
            return false
        }

        val themeValue = map.getString(KEY_THEME, null) ?: return false

        // Resolve the theme to check if it's translucent or floating
        val client = context.client
        val resources = client.getResources(mainProject, true)

        return isTranslucentOrFloatingTheme(themeValue, resources, mainProject)
    }

    private fun isTranslucentOrFloatingTheme(
        themeValue: String,
        resources: com.android.ide.common.resources.ResourceRepository,
        project: com.android.tools.lint.detector.api.Project,
    ): Boolean {
        // Check if the theme reference resolves to a translucent or floating theme
        // by looking at windowIsTranslucent or windowIsFloating attributes
        if (themeValue.startsWith("@") || themeValue.startsWith("?")) {
            val themeName = themeValue.substringAfterLast("/")
            // Check known translucent theme names as a heuristic
            if (isKnownTranslucentThemeName(themeName)) {
                return true
            }
        }
        return false
    }

    private fun isKnownTranslucentThemeName(themeName: String): Boolean {
        val lower = themeName.lowercase()
        return lower.contains("translucent") ||
            lower.contains("floating") ||
            lower.contains("dialog") ||
            lower.contains("transparent")
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return orientation in FIXED_ORIENTATIONS
    }

    override fun getApplicableMethodNames(): List<String> = SET_ORIENTATION_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val targetSdk = context.mainProject.targetSdk
        if (targetSdk < MIN_SDK_WITH_ISSUE) return

        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: return

        if (!className.endsWith("Activity") &&
            className != "android.app.Activity" &&
            !isActivityClass(context, containingClass)
        ) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val orientationArg = arguments[0]
        val evaluator = context.evaluator

        val value = evaluator.getConstantValue(orientationArg)
        if (value is Int) {
            if (!isFixedOrientationConstant(value)) return

            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Should not specify screen orientation with translucent or floating theme",
            )
            val map = LintMap()
            map.put(KEY_ORIENTATION, value.toString())
            context.report(incident, map)
        }
    }

    private fun isActivityClass(
        context: JavaContext,
        psiClass: com.intellij.psi.PsiClass,
    ): Boolean {
        return context.evaluator.extendsClass(psiClass, "android.app.Activity", false)
    }

    private fun isFixedOrientationConstant(value: Int): Boolean {
        // ActivityInfo orientation constants for fixed orientations
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
}