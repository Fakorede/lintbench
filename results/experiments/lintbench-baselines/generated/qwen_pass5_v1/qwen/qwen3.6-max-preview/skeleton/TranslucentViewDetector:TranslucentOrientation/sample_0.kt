package com.android.tools.lint.checks

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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported on apps with targetSdkVersion O or greater since there can be another activity visible behind your activity with a conflicting request. Devices running platform version O or greater will throw an exception in your app if this state is detected.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape", "locked",
            "userPortrait", "userLandscape"
        )

        private val FIXED_ORIENTATION_CONSTANTS = setOf(
            "SCREEN_ORIENTATION_PORTRAIT", "SCREEN_ORIENTATION_LANDSCAPE",
            "SCREEN_ORIENTATION_SENSOR_PORTRAIT", "SCREEN_ORIENTATION_SENSOR_LANDSCAPE",
            "SCREEN_ORIENTATION_REVERSE_PORTRAIT", "SCREEN_ORIENTATION_REVERSE_LANDSCAPE",
            "SCREEN_ORIENTATION_LOCKED", "SCREEN_ORIENTATION_USER_PORTRAIT",
            "SCREEN_ORIENTATION_USER_LANDSCAPE"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("screenOrientation")

    override fun getApplicableElements(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.MANIFEST

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!isFixedOrientation(attribute.value)) return

        val element = attribute.ownerElement
        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, "theme")
        val theme = themeAttr?.value ?: ""

        if (isTranslucentTheme(theme)) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Fixed screen orientation is not supported with translucent themes on API 26+"
            )
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Logic handled in visitAttribute
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val resolved = context.evaluator.resolve(arg) as? PsiField ?: return

        if (resolved.containingClass?.qualifiedName == "android.content.pm.ActivityInfo" &&
            resolved.name in FIXED_ORIENTATION_CONSTANTS) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Calling setRequestedOrientation with a fixed orientation may crash on API 26+ if the activity uses a translucent theme"
            )
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean =
        orientation in FIXED_ORIENTATIONS

    private fun isTranslucentTheme(theme: String): Boolean =
        theme.contains("Translucent", ignoreCase = true) ||
        theme.contains("Dialog", ignoreCase = true) ||
        theme.contains("Alert", ignoreCase = true)
}