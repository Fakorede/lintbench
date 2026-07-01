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

        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape", "userPortrait", "userLandscape"
        )

        private val FIXED_ORIENTATION_VALUES = setOf(
            0, 1, 6, 7, 8, 9, 11, 12
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with targetSdkVersion O or greater since there can be another activity visible " +
                "behind your activity with a conflicting request. Devices running platform version O or " +
                "greater will throw an exception in your app if this state is detected.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("screenOrientation")

    override fun getApplicableElements(): Collection<String>? = listOf("activity")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value in FIXED_ORIENTATIONS) {
            val element = attribute.ownerElement
            val theme = element.getAttributeNS(ANDROID_URI, "theme")
            if (theme.contains("Translucent", ignoreCase = true)) {
                context.report(
                    ISSUE,
                    context.getLocation(attribute),
                    "Fixed screen orientation is not supported with translucent themes on Android O+"
                )
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Attribute-level checking is sufficient for this detector
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdk ?: context.mainProject.targetSdk ?: 0
        return targetSdk >= 26
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val value = context.evaluator.getInt(arg) ?: return
        if (value in FIXED_ORIENTATION_VALUES) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Fixed screen orientation is not supported with translucent themes on Android O+"
            )
        }
    }
}