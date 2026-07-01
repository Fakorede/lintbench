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
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"

        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the `SecureRandom` instance to return a
                predictable sequence of numbers. This may be useful for testing but it is not
                appropriate for secure use.

                See https://goo.gle/SecureRandom and
                https://developer.android.com/reference/java/security/SecureRandom.html
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("SecureRandom", "setSeed")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.isConstructor &&
            method.name == "SecureRandom" &&
            node.valueArgumentCount > 0
        ) {
            report(context, node)
            return
        }

        if (method.name == "setSeed" &&
            node.valueArgumentCount > 0 &&
            isSecureRandomCall(context, node)
        ) {
            report(context, node)
        }
    }

    private fun isSecureRandomCall(context: JavaContext, node: UCallExpression): Boolean {
        if (methodInClass(context, node, SECURE_RANDOM_CLASS)) {
            return true
        }

        val receiver = node.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return false
        return context.evaluator.extendsClass(receiverClass, SECURE_RANDOM_CLASS, false)
    }

    private fun methodInClass(
        context: JavaContext,
        node: UCallExpression,
        className: String,
    ): Boolean {
        val method = node.resolve() ?: return false
        return context.evaluator.isMemberInClass(method, className)
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using a fixed seed with `SecureRandom` produces a predictable sequence of random numbers.",
        )
    }
}