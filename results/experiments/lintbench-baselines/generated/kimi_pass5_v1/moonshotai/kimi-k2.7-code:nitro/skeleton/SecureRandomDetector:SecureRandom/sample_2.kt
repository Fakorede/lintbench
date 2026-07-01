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

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable
                sequence of numbers. This may be useful for testing but it is not
                appropriate for secure use.
            """.trimIndent(),
            moreInfo = "https://goo.gle/SecureRandom",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("SecureRandom", "setSeed")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!isJavaSecuritySecureRandom(method)) {
            return
        }

        if (method.name == "setSeed" || node.valueArgumentCount > 0) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a fixed seed with `SecureRandom` can produce a predictable sequence of random numbers",
            )
        }
    }

    private fun isJavaSecuritySecureRandom(method: PsiMethod): Boolean =
        method.containingClass?.qualifiedName == "java.security.SecureRandom"
}