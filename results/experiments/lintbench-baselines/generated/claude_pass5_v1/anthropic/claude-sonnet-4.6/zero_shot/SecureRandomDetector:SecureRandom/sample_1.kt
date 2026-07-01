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
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastLiteralUtils
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
            moreInfo = "https://goo.gle/SecureRandom",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val RANDOM_CLASS = "java.util.Random"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD)
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SECURE_RANDOM_CLASS)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!node.isMethodCall()) return

        val methodName = method.name
        if (methodName != SET_SEED_METHOD) return

        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != SECURE_RANDOM_CLASS && qualifiedName != RANDOM_CLASS) return

        // Check if the receiver is a SecureRandom instance
        val receiver = node.receiver
        if (receiver != null) {
            val receiverType = receiver.getExpressionType()
            val typeName = receiverType?.canonicalText
            if (typeName != SECURE_RANDOM_CLASS) return
        } else if (qualifiedName != SECURE_RANDOM_CLASS) {
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

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!node.isConstructorCall()) return

        val containingClass = constructor.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != SECURE_RANDOM_CLASS) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        // SecureRandom(byte[] seed) constructor - passing a fixed seed
        val seedArgument = arguments[0]
        if (isFixedSeed(seedArgument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `new SecureRandom(seed)` with a fixed seed: it is not secure. Use `new SecureRandom()`."
            )
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        // Direct numeric or string literals are fixed seeds
        if (expression is ULiteralExpression) {
            return expression.value != null
        }

        // Negated literals or simple constant expressions
        if (UastLiteralUtils.isZeroLiteral(expression)) {
            return true
        }

        // Check for unary minus applied to a literal (e.g., -1L)
        val sourcePsi = expression.sourcePsi
        if (sourcePsi != null) {
            val text = sourcePsi.text?.trim() ?: ""
            // Simple heuristic: if it's a numeric literal possibly with sign
            if (text.matches(Regex("-?\\d+[lLfFdD]?"))) {
                return true
            }
            // Byte array literals like new byte[]{1,2,3}
            if (text.startsWith("new byte[]") || text.startsWith("new byte[] ")) {
                return true
            }
        }

        // Check for constant field references (e.g., MY_SEED constant)
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved != null) {
                // If it resolves to a field, it might be a constant
                // We can't easily determine if it's truly constant without more analysis,
                // but we flag obvious cases
            }
        }

        return false
    }
}