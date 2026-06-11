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
import org.jetbrains.uast.ULiteralExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "SecureRandomWithFixedSeed",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable sequence of numbers. This may be useful for testing but it is not appropriate for secure use.
                
                Reference documentation:
                  - https://goo.gle/SecureRandom
                  - https://developer.android.com/reference/java/security/SecureRandom.html
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setSeed")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "java.security.SecureRandom")) {
            val seedArgument = node.valueArguments.firstOrNull()
            if (seedArgument is ULiteralExpression) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using a fixed seed with `SecureRandom` is not appropriate for secure use."
                )
            }
        }
    }
}