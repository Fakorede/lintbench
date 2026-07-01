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

        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape", "locked", "userPortrait", "userLandscape"
        )

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

    override fun getApplicableElements(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value in FIXED_ORIENTATIONS) {
            val incident = Incident(
                context,
                ISSUE,
                attribute,
                "Specifying a fixed screenOrientation with a translucent theme is not supported on Android O+ and will cause a crash."
            )
            context.report(incident)
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Orientation checking is handled via visitAttribute
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdkVersion ?: 0
        return targetSdk >= 26
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val incident = Incident(
            context,
            ISSUE,
            node,
            "Calling setRequestedOrientation() on a translucent activity is not supported on Android O+ and will cause a crash."
        )
        context.report(incident)
    }
}