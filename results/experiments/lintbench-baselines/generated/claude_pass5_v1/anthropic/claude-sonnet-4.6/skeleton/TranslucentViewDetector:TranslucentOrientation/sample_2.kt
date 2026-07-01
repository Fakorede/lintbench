package com.android.tools.lint.checks

import com.android.SdkConstants
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

class TranslucentViewDetector : Detector(), Detector.UastScannerMixin, Detector.XmlScannerMixin {

    companion object {
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_THEME = "theme"
        private const val TAG_ACTIVITY = "activity"
        private const val SET_REQUESTED_ORIENTATION = "setRequestedOrientation"
        private const val SET_TRANSLUCENT = "setTranslucent"
        private const val KEY_TARGET_SDK = "targetSdk"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_TRANSLUCENT = "translucent"
        private const val MIN_SDK_VERSION = 26 // Android O

        // Screen orientations that are "fixed" (not sensor/user-driven)
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

        // Values for ActivityInfo.SCREEN_ORIENTATION_* that are fixed
        private val FIXED_ORIENTATION_CONSTANTS = setOf(
            "SCREEN_ORIENTATION_LANDSCAPE",
            "SCREEN_ORIENTATION_PORTRAIT",
            "SCREEN_ORIENTATION_REVERSE_LANDSCAPE",
            "SCREEN_ORIENTATION_REVERSE_PORTRAIT",
            "SCREEN_ORIENTATION_SENSOR_LANDSCAPE",
            "SCREEN_ORIENTATION_SENSOR_PORTRAIT",
            "SCREEN_ORIENTATION_USER_LANDSCAPE",
            "SCREEN_ORIENTATION_USER_PORTRAIT",
            "SCREEN_ORIENTATION_LOCKED"
        )

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

        private fun isFixedOrientation(orientation: String): Boolean {
            return FIXED_ORIENTATIONS.contains(orientation)
        }

        private fun isTranslucentTheme(theme: String): Boolean {
            val lower = theme.lowercase()
            return lower.contains("translucent") || lower.contains("transparent") ||
                lower.contains("floating") || lower.contains("dialog")
        }
    }

    // Track activities with fixed orientation from XML
    // Map from activity name to orientation attribute location
    private val activitiesWithFixedOrientation = mutableMapOf<String, Attr>()

    // Track activities with translucent themes from XML
    private val activitiesWithTranslucentTheme = mutableMapOf<String, Attr>()

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
                if (isFixedOrientation(orientation)) {
                    val activityName = element.getAttributeNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME
                    ) ?: return

                    // Check if this activity also has a translucent theme
                    val themeAttr = element.getAttributeNodeNS(
                        SdkConstants.ANDROID_URI,
                        ATTR_THEME
                    )
                    if (themeAttr != null && isTranslucentTheme(themeAttr.value)) {
                        reportXmlIssue(context, attribute, themeAttr)
                    } else {
                        activitiesWithFixedOrientation[activityName] = attribute
                    }
                }
            }
            ATTR_THEME -> {
                val theme = attribute.value ?: return
                if (isTranslucentTheme(theme)) {
                    val activityName = element.getAttributeNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME
                    ) ?: return

                    // Check if this activity also has a fixed orientation
                    val orientationAttr = element.getAttributeNodeNS(
                        SdkConstants.ANDROID_URI,
                        ATTR_SCREEN_ORIENTATION
                    )
                    if (orientationAttr != null && isFixedOrientation(orientationAttr.value)) {
                        reportXmlIssue(context, orientationAttr, attribute)
                    } else {
                        activitiesWithTranslucentTheme[activityName] = attribute
                    }
                }
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ACTIVITY) return

        val orientationAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            ATTR_SCREEN_ORIENTATION
        )
        val themeAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            ATTR_THEME
        )

        if (orientationAttr != null && themeAttr != null) {
            val orientation = orientationAttr.value
            val theme = themeAttr.value
            if (isFixedOrientation(orientation) && isTranslucentTheme(theme)) {
                reportXmlIssue(context, orientationAttr, themeAttr)
            }
        }
    }

    private fun reportXmlIssue(context: XmlContext, orientationAttr: Attr, themeAttr: Attr) {
        val message = "Should not specify `screenOrientation` with a translucent or floating theme"
        val incident = Incident(
            ISSUE,
            orientationAttr,
            context.getLocation(orientationAttr),
            message
        )
        val map = LintMap()
        map.put(KEY_TARGET_SDK, true)
        context.report(incident, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        return mainProject.targetSdk >= MIN_SDK_VERSION
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_REQUESTED_ORIENTATION, SET_TRANSLUCENT)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        when (methodName) {
            SET_REQUESTED_ORIENTATION -> {
                val args = node.valueArguments
                if (args.isEmpty()) return

                val arg = args[0]
                val argText = arg.asSourceString()

                val isFixed = FIXED_ORIENTATION_CONSTANTS.any { argText.contains(it) }
                if (!isFixed) return

                val message = "Should not specify a fixed `screenOrientation` with a translucent " +
                    "or floating theme; this combination is not supported on API 26 and higher"

                val incident = Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
                val map = LintMap()
                map.put(KEY_ORIENTATION, true)
                context.report(incident, map)
            }
            SET_TRANSLUCENT -> {
                val args = node.valueArguments
                if (args.isEmpty()) return

                val arg = args[0]
                val value = arg.evaluate()
                if (value == true) {
                    val message = "Should not use `setTranslucent(true)` with a fixed screen " +
                        "orientation; this combination is not supported on API 26 and higher"

                    val incident = Incident(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                    val map = LintMap()
                    map.put(KEY_TRANSLUCENT, true)
                    context.report(incident, map)
                }
            }
        }
    }
}