package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.resources.ScreenOrientation
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

    // -------------------------------------------------------------------------
    // XmlScanner – manifest checks
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_SCREEN_ORIENTATION)

    /**
     * Called for every `android:screenOrientation` attribute found in the manifest.
     * We check whether the containing `<activity>` element also specifies a translucent theme.
     */
    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Only care about the android namespace
        if (attribute.namespaceURI != ANDROID_URI) return

        val orientation = attribute.value ?: return
        if (!isFixedOrientation(orientation)) return

        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        // Check if the activity declares a translucent/floating theme directly
        val themeAttr = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (themeAttr.isNotEmpty() && isTranslucentTheme(themeAttr)) {
            reportManifestIssue(context, attribute, orientation)
            return
        }

        // Also check android:style if present
        val styleAttr = element.getAttributeNS(ANDROID_URI, ATTR_STYLE)
        if (styleAttr.isNotEmpty() && isTranslucentTheme(styleAttr)) {
            reportManifestIssue(context, attribute, orientation)
        }
    }

    /**
     * Called for every `<activity>` element. We look for the combination of
     * a fixed `screenOrientation` AND a translucent theme on the same element.
     */
    override fun visitElement(context: XmlContext, element: Element) {
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value ?: return
        if (!isFixedOrientation(orientation)) return

        val themeAttr = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (themeAttr.isNotEmpty() && isTranslucentTheme(themeAttr)) {
            reportManifestIssue(context, orientationAttr, orientation)
            return
        }

        val styleAttr = element.getAttributeNS(ANDROID_URI, ATTR_STYLE)
        if (styleAttr.isNotEmpty() && isTranslucentTheme(styleAttr)) {
            reportManifestIssue(context, orientationAttr, orientation)
        }
    }

    private fun reportManifestIssue(context: XmlContext, attribute: Attr, orientation: String) {
        val location = context.getValueLocation(attribute)
        val message = buildMessage(orientation)
        val incident = Incident(ISSUE, attribute.ownerElement, location, message)
        context.report(incident, targetSdkAtLeast(O_SDK_INT))
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – Java/Kotlin source checks
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setRequestedOrientation",
        "setTheme"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        when (method.name) {
            "setRequestedOrientation" -> {
                if (!evaluator.extendsClass(containingClass, "android.app.Activity", true)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = arg.evaluate()
                if (value is Int && isFixedOrientationInt(value)) {
                    val location = context.getLocation(node)
                    val incident = Incident(
                        ISSUE,
                        node,
                        location,
                        "Mixing a fixed screen orientation (value=$value) with a translucent " +
                                "theme isn't supported on apps targeting O or greater"
                    )
                    context.report(incident, targetSdkAtLeast(O_SDK_INT))
                }
            }

            "setTheme" -> {
                if (!evaluator.extendsClass(containingClass, "android.app.Activity", true) &&
                    !evaluator.extendsClass(containingClass, "android.view.ContextThemeWrapper", true)
                ) return
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = arg.evaluate()
                if (value is Int) {
                    // We cannot fully resolve theme attributes at this point,
                    // but flag known translucent theme resource names via the argument text.
                    val argText = arg.asSourceString()
                    if (isTranslucentTheme(argText)) {
                        val location = context.getLocation(node)
                        val incident = Incident(
                            ISSUE,
                            node,
                            location,
                            "Setting a translucent or floating theme on an Activity that has a " +
                                    "fixed screen orientation isn't supported on apps targeting O or greater"
                        )
                        context.report(incident, targetSdkAtLeast(O_SDK_INT))
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // filterIncident – conditional suppression / enrichment
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Allow the incident to proceed; targetSdkAtLeast constraint is already applied
        // at report time. Return false to keep the incident (true would suppress it).
        return false
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun isFixedOrientation(value: String): Boolean {
        return when (value.lowercase()) {
            "portrait",
            "landscape",
            "sensorPortrait",
            "sensorLandscape",
            "reversePortrait",
            "reverseLandscape",
            "userPortrait",
            "userLandscape",
            "nosensor",
            "locked",
            "sensor_portrait",
            "sensor_landscape",
            "reverse_portrait",
            "reverse_landscape",
            "user_portrait",
            "user_landscape" -> true
            else -> false
        }
    }

    /**
     * Maps ActivityInfo orientation constants to "fixed" orientations.
     * Values taken from android.content.pm.ActivityInfo.
     */
    private fun isFixedOrientationInt(value: Int): Boolean {
        return when (value) {
            0,   // SCREEN_ORIENTATION_UNSPECIFIED – not fixed, skip
            -> false
            1,   // SCREEN_ORIENTATION_LANDSCAPE
            2,   // SCREEN_ORIENTATION_PORTRAIT
            4,   // SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            5,   // SCREEN_ORIENTATION_SENSOR_PORTRAIT
            6,   // SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            7,   // SCREEN_ORIENTATION_REVERSE_PORTRAIT
            8,   // SCREEN_ORIENTATION_SENSOR
            9,   // SCREEN_ORIENTATION_FULL_SENSOR
            10,  // SCREEN_ORIENTATION_NOSENSOR
            11,  // SCREEN_ORIENTATION_USER_LANDSCAPE
            12,  // SCREEN_ORIENTATION_USER_PORTRAIT
            13,  // SCREEN_ORIENTATION_FULL_USER
            14   // SCREEN_ORIENTATION_LOCKED
            -> true
            else -> false
        }
    }

    private fun isTranslucentTheme(themeValue: String): Boolean {
        val lower = themeValue.lowercase()
        return lower.contains("translucent") ||
                lower.contains("floating") ||
                lower.contains("dialog") ||
                lower.contains("transparent")
    }

    private fun buildMessage(orientation: String): String {
        return "Mixing a fixed screen orientation (`$orientation`) with a translucent theme " +
                "isn't supported on apps with `targetSdkVersion` O or greater. Devices running " +
                "platform version O or greater will throw an exception in your app if this state " +
                "is detected."
    }

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        private const val O_SDK_INT = 26

        @JvmField
        val ISSUE: Issue = Issue.create(
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
    }
}