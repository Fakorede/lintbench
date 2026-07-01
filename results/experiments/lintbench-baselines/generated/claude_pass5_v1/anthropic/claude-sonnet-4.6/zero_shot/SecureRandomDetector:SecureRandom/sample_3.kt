package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UNewArrayExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isNewObjectWithQualifiedName

class SecureRandomDetector : Detector(), Detector.UastScanner {

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

        private val RANDOM_CLASS = "java.util.Random"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(SET_SEED_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != SECURE_RANDOM_CLASS &&
            !context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)
        ) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val seedArgument = arguments[0]

        if (isFixedSeed(seedArgument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: it is not secure. Use `getSeed()`."
            )
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> true
            is UNewArrayExpression -> {
                // new byte[] { ... } - check if all elements are literals
                val initializer = expression.initializers
                initializer.isNotEmpty() && initializer.all { it is ULiteralExpression }
            }
            else -> {
                // Check if it's a constant reference or evaluate to a constant
                val evaluated = expression.evaluate()
                evaluated != null
            }
        }
    }
}