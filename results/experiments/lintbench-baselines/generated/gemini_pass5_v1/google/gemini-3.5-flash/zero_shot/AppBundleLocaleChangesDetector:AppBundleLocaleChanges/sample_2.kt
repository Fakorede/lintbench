package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                if (method.name == "setLocale" || method.name == "setLocales") {
                    if (context.evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
                        reportIssue(context, node)
                    }
                }
            }

            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator == UastBinaryOperator.ASSIGN) {
                    val left = node.leftOperand
                    if (left is UReferenceExpression) {
                        val resolved = left.resolve()
                        if (resolved is PsiField && resolved.name == "locale") {
                            val containingClass = resolved.containingClass
                            if (containingClass?.qualifiedName == "android.content.res.Configuration") {
                                reportIssue(context, node)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "When changing locales at runtime, configure the Android App Bundle to not split by locale " +
                "(`bundle { language { enableSplit = false } }` in `build.gradle`) or use the " +
                "Play Core library to download additional locales."
        )
    }
}