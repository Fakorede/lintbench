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

class SecureRandomDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("setSeed")

    override fun getApplicableConstructorTypes(): List<String>? = listOf("java.security.SecureRandom")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName == "java.security.SecureRandom") {
            reportIssue(context, node)
        }
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.valueArgumentCount > 0) {
            reportIssue(context, node)
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            context.getLocation(node),
            "Do not specify a fixed seed for SecureRandom"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom"
        )
    }
}