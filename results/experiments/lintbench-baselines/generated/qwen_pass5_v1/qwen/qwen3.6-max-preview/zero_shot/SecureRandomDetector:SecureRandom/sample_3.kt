package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class SecureRandomDetector : Detector(), Detector.UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("setSeed", "SecureRandom")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "java.security.SecureRandom") return

        val isSeedUsage = method.name == "setSeed" ||
                (method.isConstructor && node.valueArguments.isNotEmpty())

        if (isSeedUsage) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Do not specify a seed for SecureRandom"
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with SecureRandom",
            explanation = "Specifying a fixed seed will cause the instance to return a predictable sequence of numbers. This may be useful for testing but it is not appropriate for secure use.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}