package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val O_API = 26

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme isn't supported on apps with targetSdkVersion O or greater since there can be another activity visible behind your activity with a conflicting request. Devices running platform version O or greater will throw an exception in your app if this state is detected.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("activity")

    override fun getApplicableAttributes(): Collection<String>? = listOf("screenOrientation")

    override fun appliesTo(context: Context, file: File): Boolean = true

    override fun visitElement(context: XmlContext, element: Element) {
        // Structural override to satisfy XmlScanner contract.
        // Theme analysis is deferred to visitAttribute to centralize reporting logic
        // and prevent duplicate incidents for the same manifest node.
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement
        val theme = element.getAttributeNS(ANDROID_URI, "theme")
        val isExplicitlyTranslucent = theme.contains("Translucent", ignoreCase = true)

        val message = if (isExplicitlyTranslucent) {
            "Mixing screenOrientation and a translucent theme is not supported on Android O+ and will throw an exception."
        } else {
            "Specifying a fixed screenOrientation on a potentially translucent activity is not supported on Android O+ and will throw an exception."
        }

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            message
        )
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return context.project.targetSdkVersion >= O_API
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling setRequestedOrientation on a translucent activity is not supported on Android O+ and will throw an exception."
            )
        }
    }
}