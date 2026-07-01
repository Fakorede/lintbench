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
        val evaluator = context.evaluator

        if (!evaluator.inheritsFrom(containingClass, SECURE_RANDOM_CLASS, false)) {
            return
        }

        val methodName = method.name
        val arguments = node.valueArguments

        when {
            methodName == SET_SEED_METHOD -> {
                // setSeed(long) or setSeed(byte[]) — flag if argument is a fixed/literal value
                if (arguments.isNotEmpty()) {
                    val arg = arguments[0]
                    if (isFixedSeed(arg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                                "it is not secure. Use `getSeed()` or leave it unseeded.",
                        )
                    }
                }
            }

            methodName == CONSTRUCTOR_NAME -> {
                // new SecureRandom(byte[] seed) — flag if a seed argument is provided and fixed
                if (arguments.isNotEmpty()) {
                    val arg = arguments[0]
                    if (isFixedSeed(arg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not seed `SecureRandom` with a fixed seed: it is not secure. " +
                                "Use the no-argument constructor or `generateSeed()`.",
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given expression looks like a fixed / predictable seed value.
     * This includes numeric and string literals, as well as references to constants.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> {
                // Any literal (number, string, etc.) is considered a fixed seed
                expression.value != null
            }
            is UReferenceExpression -> {
                // A reference to a constant field is also considered fixed
                val resolved = expression.resolve()
                resolved != null
            }
            else -> false
        }
    }
}