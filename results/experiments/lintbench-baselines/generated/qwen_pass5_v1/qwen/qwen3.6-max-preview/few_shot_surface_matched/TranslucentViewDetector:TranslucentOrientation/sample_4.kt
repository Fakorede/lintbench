package com.android.tools.lint.checks

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
import org.jetbrains.uast.UCallExpression

class TranslucentViewDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val API_O = 26
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("screenOrientation")
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "application")
    }

    override fun appliesTo(context: Context, file: java.io.File): Boolean {
        return context.project.targetSdk >= API_O
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val element = attribute.ownerElement
        val theme = element.getAttributeNS(ANDROID_URI, "theme")
        if (theme.contains("Translucent", ignoreCase = true)) {
            val orientation = attribute.value
            if (orientation != "unspecified" && orientation != "behind" &&
                orientation != "user" && orientation != "fullSensor") {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Specifying a fixed screenOrientation with a translucent theme is not supported on API 26+"
                )
            }
        }
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        // Application-level theme checks or additional manifest validation can be performed here.
        // The primary orientation/theme conflict is handled in visitAttribute.
    }

    override fun filterIncident(incident: Incident): Boolean {
        return incident.context.project.targetSdk >= API_O
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling setRequestedOrientation on an Activity with a translucent theme is not supported on API 26+"
            )
        }
    }
}