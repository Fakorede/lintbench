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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/SecureRandom",
        )

        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
        private const val CONSTRUCTOR_NAME = "SecureRandom"

        private val APPLICABLE_METHODS = listOf(SET_SEED_METHOD, CONSTRUCTOR_NAME)
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != SECURE_RANDOM_CLASS) return

        val methodName = method.name
        val arguments = node.valueArguments

        when {
            methodName == SET_SEED_METHOD -> {
                // setSeed(long) or setSeed(byte[]) — any fixed seed call is suspicious
                if (arguments.isNotEmpty()) {
                    val seedArg = arguments[0]
                    if (isFixedSeed(seedArg)) {
                        reportIssue(context, node, seedArg)
                    }
                }
            }
            methodName == CONSTRUCTOR_NAME -> {
                // new SecureRandom(byte[]) constructor — seeded constructor
                if (arguments.isNotEmpty()) {
                    val seedArg = arguments[0]
                    if (isFixedSeed(seedArg)) {
                        reportIssue(context, node, seedArg)
                    }
                }
            }
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> true
            is UReferenceExpression -> {
                // Could be a constant reference — treat as fixed seed
                val resolved = expression.resolve()
                resolved != null
            }
            else -> false
        }
    }

    private fun reportIssue(
        context: JavaContext,
        node: UCallExpression,
        seedArg: UExpression,
    ) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(seedArg),
            message = "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                "it is not secure. Use `getSeed()`.",
        )
    }
}