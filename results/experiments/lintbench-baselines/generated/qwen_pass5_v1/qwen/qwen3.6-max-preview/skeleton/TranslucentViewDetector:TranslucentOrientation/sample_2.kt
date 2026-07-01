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
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val TAG_ACTIVITY = "activity"

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported " +
                "on apps with targetSdkVersion O or greater since there can be another activity visible behind " +
                "your activity with a conflicting request. Devices running platform version O or greater will " +
                "throw an exception in your app if this state is detected.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val FIXED_ORIENTATIONS = setOf(
            "landscape", "portrait", "sensorLandscape", "sensorPortrait",
            "reverseLandscape", "reversePortrait", "locked",
            "userLandscape", "userPortrait"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf(ATTR_SCREEN_ORIENTATION)

    override fun getApplicableElements(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ACTIVITY) return

        val targetSdk = context.project.targetSdkVersion
        if (targetSdk != null && targetSdk < 26) return

        val value = attribute.value
        if (value in FIXED_ORIENTATIONS) {
            val message = "Specifying a fixed screenOrientation on an activity that uses a translucent theme " +
                "is not supported when targetSdkVersion is 26 or higher. This will cause a crash on Android O+ devices."
            context.report(Incident(context, ISSUE, attribute, message))
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Handled via attribute scanning
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass
        if (containingClass?.qualifiedName != "android.app.Activity") return

        val targetSdk = context.project.targetSdkVersion
        if (targetSdk != null && targetSdk < 26) return

        val message = "Calling setRequestedOrientation() on an activity that uses a translucent theme " +
            "is not supported when targetSdkVersion is 26 or higher. This will cause a crash on Android O+ devices."
        context.report(Incident(context, ISSUE, node, message))
    }
}