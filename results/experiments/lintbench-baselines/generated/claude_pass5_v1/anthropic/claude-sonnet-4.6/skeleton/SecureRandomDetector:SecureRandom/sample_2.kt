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
        private const val METHOD_CONSTRUCTOR = "<init>"

        private val APPLICABLE_METHODS = listOf(SET_SEED_METHOD, METHOD_CONSTRUCTOR)
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        // Check if this call is on SecureRandom
        if (!evaluator.isMemberInClass(method, SECURE_RANDOM_CLASS)) {
            return
        }

        if (node.isConstructorCall()) {
            // SecureRandom(byte[] seed) constructor — flag if a seed argument is provided
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                // Any non-empty argument list means a seed was supplied
                val seedArg = args[0]
                if (isFixedSeed(seedArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(seedArg),
                        "Do not call `SecureRandom()` with a fixed seed: it is not secure. " +
                            "Use `SecureRandom()` (with no arguments).",
                    )
                }
            }
        } else if (method.name == SET_SEED_METHOD) {
            // SecureRandom.setSeed(long seed) or setSeed(byte[] seed)
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val seedArg = args[0]
                if (isFixedSeed(seedArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(seedArg),
                        "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                            "it is not secure.",
                    )
                }
            }
        }
    }

    /**
     * Returns true if the given expression represents a fixed (literal or constant) seed value.
     * A literal value (number, string, null) is always considered a fixed seed.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        // Literal values (e.g. 0L, 12345, byte arrays expressed as literals) are fixed seeds
        if (expression is ULiteralExpression) {
            return true
        }

        // Try constant evaluation — if the expression resolves to a compile-time constant,
        // it is effectively a fixed seed
        val constantValue = expression.evaluate()
        if (constantValue != null) {
            return true
        }

        return false
    }
}