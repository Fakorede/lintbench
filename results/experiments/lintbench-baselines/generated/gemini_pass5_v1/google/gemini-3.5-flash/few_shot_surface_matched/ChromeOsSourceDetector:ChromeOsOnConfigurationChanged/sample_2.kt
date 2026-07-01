package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    private val reportedNodes = HashSet<UCallExpression>()

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw logic in onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("recreate", "setContentView", "inflate")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isInsideOnConfigurationChanged(node)) {
            reportIssue(context, node)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Satisfies override requirement
            }

            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName
                if (methodName in listOf("recreate", "setContentView", "inflate")) {
                    if (isInsideOnConfigurationChanged(node)) {
                        reportIssue(context, node)
                    }
                }
            }
        }
    }

    private fun isInsideOnConfigurationChanged(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                if (parent.name == "onConfigurationChanged") {
                    val parameters = parent.valueParameters
                    if (parameters.size == 1 && parameters[0].type.canonicalText == "android.content.res.Configuration") {
                        return true
                    }
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        if (!reportedNodes.add(node)) {
            return
        }
        val message = "Avoid calling `${node.methodName}` inside `onConfigurationChanged()`. " +
                "This can cause a UI redraw and lead to poor performance on Chrome OS and large screens."
        context.report(
            Incident(ISSUE, node, context.getLocation(node), message)
        )
    }
}