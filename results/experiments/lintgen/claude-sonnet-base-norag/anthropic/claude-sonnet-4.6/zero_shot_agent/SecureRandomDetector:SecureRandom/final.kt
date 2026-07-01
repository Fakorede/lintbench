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
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isMethodCall

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"

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
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD)
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SECURE_RANDOM_CLASS)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!node.isMethodCall()) return

        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

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

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!node.isConstructorCall()) return

        val containingClass = constructor.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val seedArg = arguments[0]
        if (isFixedSeed(seedArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `new SecureRandom(seed)` with a fixed seed: " +
                        "it is not secure. Use `new SecureRandom()`."
            )
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        // Literal values are always fixed seeds
        if (expression is ULiteralExpression) {
            return true
        }

        // Try to evaluate the expression as a constant
        val evaluated = expression.evaluate()
        if (evaluated != null) {
            return true
        }

        // Check for constant field references
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiField) {
                val initializer = resolved.initializer
                if (initializer != null && resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)) {
                    return true
                }
            } else if (resolved is PsiVariable) {
                val initializer = resolved.initializer
                if (initializer != null && resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)) {
                    return true
                }
            }
        }

        return false
    }
}