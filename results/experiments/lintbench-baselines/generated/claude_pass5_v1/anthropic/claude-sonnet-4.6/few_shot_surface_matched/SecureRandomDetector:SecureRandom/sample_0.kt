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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
        private const val CONSTRUCTOR_NAME = "SecureRandom"

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable sequence \
                of numbers. This may be useful for testing but it is not appropriate for secure use.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom",
            androidSpecific = false
        )

        private const val MESSAGE_SET_SEED =
            "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: it undermines randomness"

        private const val MESSAGE_CONSTRUCTOR =
            "Do not use a fixed seed with `SecureRandom`: it undermines randomness"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD, CONSTRUCTOR_NAME)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        val methodName = method.name

        if (methodName == SET_SEED_METHOD) {
            // Check that this is SecureRandom.setSeed()
            if (!evaluator.isMemberInSubClassOf(method, SECURE_RANDOM_CLASS)) {
                return
            }
            val argument = node.valueArguments.firstOrNull() ?: return
            if (isFixedSeed(argument)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE_SET_SEED
                )
            }
        } else if (methodName == CONSTRUCTOR_NAME) {
            // Check that this is a SecureRandom constructor call with a fixed seed argument
            val containingClass = method.containingClass ?: return
            if (containingClass.qualifiedName != SECURE_RANDOM_CLASS) {
                return
            }
            // Only flag if a seed argument is provided (non-default constructor)
            val arguments = node.valueArguments
            if (arguments.isEmpty()) {
                return
            }
            val argument = arguments.firstOrNull() ?: return
            if (isFixedSeed(argument)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE_CONSTRUCTOR
                )
            }
        }
    }

    private fun isFixedSeed(argument: UExpression): Boolean {
        // Literal values (numbers, strings, etc.) are fixed seeds
        if (argument is ULiteralExpression) {
            return true
        }

        // Check for constant references or simple name references that resolve to constants
        if (argument is USimpleNameReferenceExpression || argument is UQualifiedReferenceExpression) {
            val resolved = argument.evaluate()
            if (resolved != null) {
                return true
            }
        }

        // Try evaluating the expression to see if it's a compile-time constant
        val evaluated = argument.evaluate()
        if (evaluated != null) {
            return true
        }

        return false
    }
}