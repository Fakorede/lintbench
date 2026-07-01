package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "SecureRandom",
            "Using a fixed seed with `SecureRandom`",
            """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not \
                appropriate for secure use.
            """,
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(SecureRandomDetector::class.java, Scope.JAVA_FILE_SCOPE),
            "https://goo.gle/SecureRandom"
        )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.security.SecureRandom")

    override fun applicableMethodNames(): List<String> = listOf("setSeed")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (node.valueArgumentCount > 0) {
            report(context, node)
        }
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (context.evaluator.isMemberInClass(method, "java.security.SecureRandom")) {
            report(context, node)
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using a fixed seed with `SecureRandom` is not secure"
        )
    }
}