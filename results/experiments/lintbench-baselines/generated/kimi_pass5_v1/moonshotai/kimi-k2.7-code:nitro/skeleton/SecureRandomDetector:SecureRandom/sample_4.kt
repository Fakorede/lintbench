package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CLASS_SECURE_RANDOM = "java.security.SecureRandom"

        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = "Specifying a fixed seed will cause the instance to return a predictable sequence of numbers. This may be useful for testing but it is not appropriate for secure use.",
            moreInfo = "https://goo.gle/SecureRandom",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("setSeed")

    override fun getApplicableConstructorTypes(): List<String>? = listOf(CLASS_SECURE_RANDOM)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val firstArg = node.valueArguments.firstOrNull()
        if (firstArg != null && isByteArray(firstArg)) {
            report(context, node)
        }
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (method.containingClass?.qualifiedName == CLASS_SECURE_RANDOM
            && node.valueArgumentCount > 0
        ) {
            report(context, node)
        }
    }

    private fun isByteArray(expression: UExpression): Boolean {
        val type = expression.getExpressionType() ?: return false
        return type is PsiArrayType && type.componentType == PsiType.BYTE
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(
                call = node,
                includeReceiver = false,
                includeArguments = true
            ),
            message = "Using a fixed seed with `SecureRandom` is not secure"
        )
    }
}