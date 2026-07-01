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
        private const val KEY_TRANSLUCENT = "translucent"
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
            "@android:style/Theme.Translucent",
            "@android:style/Theme.Translucent.NoTitleBar",
            "@android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@android:style/Theme.Dialog",
            "@android:style/Theme.DeviceDefault.Dialog",
            "@android:style/Theme.DeviceDefault.Dialog.MinWidth",
            "@android:style/Theme.DeviceDefault.Dialog.NoActionBar",
            "@android:style/Theme.DeviceDefault.Dialog.NoActionBar.MinWidth",
            "@android:style/Theme.DeviceDefault.Light.Dialog",
            "@android:style/Theme.DeviceDefault.Light.Dialog.MinWidth",
            "@android:style/Theme.DeviceDefault.Light.Dialog.NoActionBar",
            "@android:style/Theme.DeviceDefault.Light.Dialog.NoActionBar.MinWidth",
            "@android:style/Theme.Material.Dialog",
            "@android:style/Theme.Material.Dialog.MinWidth",
            "@android:style/Theme.Material.Dialog.NoActionBar",
            "@android:style/Theme.Material.Dialog.NoActionBar.MinWidth",
            "@android:style/Theme.Material.Light.Dialog",
            "@android:style/Theme.Material.Light.Dialog.MinWidth",
            "@android:style/Theme.Material.Light.Dialog.NoActionBar",
            "@android:style/Theme.Material.Light.Dialog.NoActionBar.MinWidth",
        )

        // Methods used to set orientation programmatically
        private const val SET_REQUESTED_ORIENTATION = "setRequestedOrientation"

        // android.content.pm.ActivityInfo orientation constants
        private val FIXED_ORIENTATION_CONSTANTS = setOf(
            "SCREEN_ORIENTATION_LANDSCAPE",
            "SCREEN_ORIENTATION_PORTRAIT",
            "SCREEN_ORIENTATION_REVERSE_LANDSCAPE",
            "SCREEN_ORIENTATION_REVERSE_PORTRAIT",
            "SCREEN_ORIENTATION_SENSOR_LANDSCAPE",
            "SCREEN_ORIENTATION_SENSOR_PORTRAIT",
            "SCREEN_ORIENTATION_USER_LANDSCAPE",
            "SCREEN_ORIENTATION_USER_PORTRAIT",
            "SCREEN_ORIENTATION_LOCKED",
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_SCREEN_ORIENTATION, ATTR_THEME)
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        val attrName = attribute.localName ?: return

        when (attrName) {
            ATTR_SCREEN_ORIENTATION -> {
                val orientation = attribute.value ?: return
                if (!FIXED_ORIENTATIONS.contains(orientation)) return

                // Check if same element also has a translucent theme
                val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
                if (themeAttr != null && isTranslucentTheme(themeAttr.value)) {
                    reportIncident(context, element, attribute, themeAttr)
                    return
                }

                // Store partial info for cross-file checking
                val incident = Incident(
                    ISSUE,
                    element,
                    context.getLocation(attribute),
                    "Should not specify screen orientation with translucent theme",
                )
                val map = LintMap()
                map.put(KEY_ORIENTATION, orientation)
                map.put(KEY_LOCATION, context.getLocation(attribute))
                context.report(incident, map)
            }
            ATTR_THEME -> {
                val theme = attribute.value ?: return
                if (!isTranslucentTheme(theme)) return

                // Check if same element also has a fixed orientation
                val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr != null && FIXED_ORIENTATIONS.contains(orientationAttr.value)) {
                    reportIncident(context, element, orientationAttr, attribute)
                    return
                }

                // Store partial info for cross-file checking
                val incident = Incident(
                    ISSUE,
                    element,
                    context.getLocation(attribute),
                    "Should not specify screen orientation with translucent theme",
                )
                val map = LintMap()
                map.put(KEY_TRANSLUCENT, theme)
                map.put(KEY_LOCATION, context.getLocation(attribute))
                context.report(incident, map)
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)

        if (orientationAttr == null || themeAttr == null) return

        val orientation = orientationAttr.value ?: return
        val theme = themeAttr.value ?: return

        if (FIXED_ORIENTATIONS.contains(orientation) && isTranslucentTheme(theme)) {
            reportIncident(context, element, orientationAttr, themeAttr)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report if targetSdkVersion >= O (26)
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        if (targetSdk < 26) return false

        // Report if we have both orientation and translucent info
        val hasOrientation = map.containsKey(KEY_ORIENTATION)
        val hasTranslucent = map.containsKey(KEY_TRANSLUCENT)

        // If only one side was found in the manifest, we still warn because
        // the theme might be defined elsewhere or inherited
        return hasOrientation || hasTranslucent
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_REQUESTED_ORIENTATION)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val arg = args[0]
        val argText = arg.asSourceString()

        val isFixedOrientation = FIXED_ORIENTATION_CONSTANTS.any { argText.contains(it) }
        if (!isFixedOrientation) return

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with targetSdkVersion O or greater",
        )
        val map = LintMap()
        map.put(KEY_ORIENTATION, argText)
        context.report(incident, map)
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        if (TRANSLUCENT_THEMES.contains(theme)) return true
        val lower = theme.lowercase()
        return lower.contains("translucent") ||
            lower.contains("dialog") ||
            lower.contains("floating")
    }

    private fun reportIncident(
        context: XmlContext,
        element: Element,
        orientationAttr: Attr,
        themeAttr: Attr,
    ) {
        val message = "Mixing `screenOrientation` with a translucent theme is not supported on " +
            "apps with `targetSdkVersion` O or greater. The orientation will be ignored."

        val incident = Incident(
            ISSUE,
            element,
            context.getLocation(orientationAttr),
            message,
        )
        val map = LintMap()
        map.put(KEY_ORIENTATION, orientationAttr.value ?: "")
        map.put(KEY_TRANSLUCENT, themeAttr.value ?: "")
        context.report(incident, map)
    }
}