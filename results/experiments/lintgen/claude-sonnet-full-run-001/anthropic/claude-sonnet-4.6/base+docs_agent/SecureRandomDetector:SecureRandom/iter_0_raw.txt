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
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isMethodCall

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
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

        private val CONSTRUCTOR_METHODS = listOf("SecureRandom")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD) + CONSTRUCTOR_METHODS
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName

        if (node.isConstructorCall()) {
            // Check if this is a SecureRandom constructor call with a seed argument
            if (qualifiedName == SECURE_RANDOM_CLASS) {
                val arguments = node.valueArguments
                if (arguments.isNotEmpty()) {
                    // Constructor with seed parameter - check if it's a fixed/literal seed
                    val seedArg = arguments[0]
                    if (isFixedSeed(seedArg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(seedArg),
                            "Do not call `SecureRandom` with a fixed seed; it is not secure. " +
                                    "Use `SecureRandom()` instead."
                        )
                    }
                }
            }
        } else if (node.isMethodCall()) {
            // Check setSeed calls on SecureRandom instances
            if (qualifiedName == SECURE_RANDOM_CLASS && method.name == SET_SEED_METHOD) {
                val arguments = node.valueArguments
                if (arguments.isNotEmpty()) {
                    val seedArg = arguments[0]
                    if (isFixedSeed(seedArg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(seedArg),
                            "Do not call `setSeed()` on a `SecureRandom` with a fixed seed; " +
                                    "it is not secure."
                        )
                    }
                }
            }
        }
    }

    /**
     * Determines whether the given expression represents a fixed/predictable seed value.
     * A fixed seed is one that is a literal value or a constant reference.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> true
            is UReferenceExpression -> {
                // Check if it resolves to a constant field
                val resolved = expression.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL) &&
                            resolved.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)
                } else if (resolved is com.intellij.psi.PsiLocalVariable) {
                    // Local variable - could be a constant, treat conservatively
                    val initializer = resolved.initializer
                    initializer is ULiteralExpression ||
                            (initializer != null && isLiteralOrConstant(initializer))
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun isLiteralOrConstant(expression: com.intellij.psi.PsiExpression): Boolean {
        return expression is com.intellij.psi.PsiLiteralExpression
    }
}