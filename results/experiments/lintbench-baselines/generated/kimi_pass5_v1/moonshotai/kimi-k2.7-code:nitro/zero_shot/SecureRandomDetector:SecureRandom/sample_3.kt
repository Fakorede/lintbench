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
import java.security.SecureRandom

class SecureRandomDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<Class<*>>? =
        listOf(SecureRandom::class.java)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        if (node.valueArgumentCount > 0) {
            reportIssue(context, node)
        }
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("setSeed")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val receiverType = node.receiverType
        if (receiverType != null) {
            val receiverClass = evaluator.typeClass(receiverType) ?: return
            if (!evaluator.extendsClass(receiverClass, "java.security.SecureRandom", false)) {
                return
            }
        } else if (method.containingClass?.qualifiedName != "java.security.SecureRandom") {
            return
        }
        reportIssue(context, node)
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using a fixed seed with `SecureRandom` produces a predictable sequence of numbers; " +
                "avoid fixed seeds except for testing."
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not \
                appropriate for secure use.

                See:
                - https://goo.gle/SecureRandom
                - https://developer.android.com/reference/java/security/SecureRandom.html
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}