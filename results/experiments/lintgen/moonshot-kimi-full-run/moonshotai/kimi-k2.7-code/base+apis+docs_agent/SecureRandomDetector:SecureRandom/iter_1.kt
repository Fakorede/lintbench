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

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.security.SecureRandom")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (constructor.parameterList.parameters.size == 1) {
            report(context, node)
        }
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("setSeed")

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
            "Using a fixed seed with `SecureRandom` is insecure; it produces a predictable sequence of values."
        )
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with SecureRandom",
            explanation = """
                Specifying a fixed seed will cause `SecureRandom` to return a predictable
                sequence of numbers. This may be useful for testing but it is not appropriate
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}