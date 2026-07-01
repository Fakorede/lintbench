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
import org.jetbrains.uast.UNewExpression
import org.jetbrains.uast.util.isConstructorCall

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

        private val CONSTRUCTOR_METHODS = listOf("SecureRandom")
        private val ALL_METHODS = listOf("SecureRandom", SET_SEED_METHOD)
    }

    override fun getApplicableMethodNames(): List<String> = ALL_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false) &&
            containingClass.qualifiedName != SECURE_RANDOM_CLASS
        ) {
            return
        }

        if (node.isConstructorCall()) {
            // Check if constructor is called with a fixed seed argument
            val arguments = node.valueArguments
            if (arguments.isEmpty()) {
                // No-arg constructor is fine
                return
            }
            // Constructor with seed: new SecureRandom(byte[])
            // Check if the seed argument is a constant/literal
            val seedArg = arguments[0]
            if (isFixedSeed(seedArg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call `SecureRandom()` with a fixed seed: it is not secure. " +
                        "Use `SecureRandom()` without arguments instead.",
                )
            }
        } else if (method.name == SET_SEED_METHOD) {
            val arguments = node.valueArguments
            if (arguments.isEmpty()) return
            val seedArg = arguments[0]
            if (isFixedSeed(seedArg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                        "it is not secure. Use `SecureRandom()` without arguments instead.",
                )
            }
        }
    }

    /**
     * Returns true if the given expression represents a fixed (constant/literal) seed value.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        // If it's a literal value (number, string, etc.), it's a fixed seed
        if (expression is ULiteralExpression) {
            return true
        }
        // If the expression evaluates to a constant value, it's a fixed seed
        val evaluatedValue = expression.evaluate()
        if (evaluatedValue != null) {
            return true
        }
        return false
    }
}