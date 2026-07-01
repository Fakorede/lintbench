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

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
        private const val NEXT_BYTES_METHOD = "nextBytes"

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
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = false,
            moreInfo = "https://goo.gle/SecureRandom"
        )

        private const val SET_SEED_MESSAGE =
            "It is dangerous to seed `SecureRandom` with the current time because that value is more predictable to an attacker than the default seed"
        private const val FIXED_SEED_MESSAGE =
            "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: it is not secure. Use `getSeed()`."
    }

    override fun getApplicableMethodNames(): List<String> = listOf(SET_SEED_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClassOf(method, SECURE_RANDOM_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val argument = arguments[0]

        // Check if the seed is a fixed literal (constant number)
        if (isFixedSeed(argument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                FIXED_SEED_MESSAGE
            )
            return
        }

        // Check if the seed is based on System.currentTimeMillis() or System.nanoTime()
        if (isTimeSeed(argument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                SET_SEED_MESSAGE
            )
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        // Direct numeric literal
        if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is Number) {
                return true
            }
        }
        return false
    }

    private fun isTimeSeed(expression: UExpression): Boolean {
        val text = expression.asSourceString()
        return text.contains("currentTimeMillis") || text.contains("nanoTime")
    }
}