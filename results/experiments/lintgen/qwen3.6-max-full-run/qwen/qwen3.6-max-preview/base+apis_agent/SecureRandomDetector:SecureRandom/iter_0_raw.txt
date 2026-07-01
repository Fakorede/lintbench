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

    override fun getApplicableMethodNames(): List<String>? = listOf("SecureRandom", "setSeed")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "java.security.SecureRandom") {
            return
        }

        val methodName = node.methodName
        if (methodName == "SecureRandom" && node.valueArgumentCount == 0) {
            return
        }

        if (methodName == "SecureRandom" || methodName == "setSeed") {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "Do not specify a seed for SecureRandom"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "SecureRandom",
            "Using a fixed seed with SecureRandom",
            "Specifying a fixed seed will cause the instance to return a predictable " +
                "sequence of numbers. This may be useful for testing but it is not appropriate " +
                "for secure use.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(SecureRandomDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}