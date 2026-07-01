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

        private val SAFE_ORIENTATIONS = setOf(
            "unspecified", "behind", "user", "fullUser", "sensor", "fullSensor", "nosensor"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("screenOrientation")

    override fun getApplicableElements(): Collection<String>? = listOf("activity")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if ((context.mainProject.targetSdkVersion ?: 0) < 26) return
        val value = attribute.value
        if (value !in SAFE_ORIENTATIONS) {
            context.report(
                Incident(ISSUE)
                    .at(attribute)
                    .message("Specifying a fixed screenOrientation with a translucent theme is not supported on apps with targetSdkVersion 26 or higher and will cause a crash.")
            )
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Orientation constraints are checked via visitAttribute
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if ((context.mainProject.targetSdkVersion ?: 0) < 26) return
        context.report(
            Incident(ISSUE)
                .at(node)
                .message("Calling setRequestedOrientation with a fixed orientation on an activity with a translucent theme is not supported on apps with targetSdkVersion 26 or higher and will cause a crash.")
        )
    }
}