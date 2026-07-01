package com.android.tools.lint.checks

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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastPrefixOperator

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            moreInfo = "https://goo.gle/SecureRandom",
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD)
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SECURE_RANDOM_CLASS)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != SECURE_RANDOM_CLASS) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
            val seedArg = arguments[0]
            if (isFixedSeed(seedArg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                            "it is not secure. Use `getSeed()`."
                )
            }
        }
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
            val seedArg = arguments[0]
            if (isFixedSeed(seedArg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not seed `SecureRandom` with a fixed seed: it is not secure. " +
                            "Use `getSeed()`."
                )
            }
        }
    }

    /**
     * Returns true if the given expression represents a fixed/literal seed value
     * that would make SecureRandom predictable.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> true
            is UPrefixExpression -> {
                // Handle negative literals like -1L, -12345
                val operator = expression.operator
                if (operator == UastPrefixOperator.UNARY_MINUS) {
                    expression.operand is ULiteralExpression
                } else {
                    false
                }
            }
            is UReferenceExpression -> {
                // Could be a constant reference - flag static final fields
                val resolved = expression.resolve()
                if (resolved is PsiField) {
                    resolved.hasModifierProperty(PsiModifier.FINAL) &&
                            resolved.hasModifierProperty(PsiModifier.STATIC)
                } else {
                    false
                }
            }
            else -> false
        }
    }
}