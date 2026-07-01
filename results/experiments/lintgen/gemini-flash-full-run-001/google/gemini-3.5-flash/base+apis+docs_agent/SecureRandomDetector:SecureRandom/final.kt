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
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/SecureRandom",
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("java.security.SecureRandom")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (node.valueArgumentCount > 0) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `SecureRandom` constructor with a seed"
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setSeed")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (context.evaluator.isMemberInClass(method, "java.security.SecureRandom")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `setSeed` on a `SecureRandom` instance"
            )
        }
    }
}