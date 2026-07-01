package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import org.jetbrains.uast.UCallExpression

class SecureRandomDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("setSeed")

    override fun getApplicableConstructorTypes(): List<String>? = listOf("java.security.SecureRandom")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "java.security.SecureRandom")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not specify a seed for SecureRandom"
            )
        }
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val params = constructor.parameterList.parameters
        if (params.isNotEmpty() &&
            params[0].type is PsiArrayType &&
            (params[0].type as PsiArrayType).componentType == PsiTypes.byteType()
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not specify a seed for SecureRandom"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "SecureRandom",
            "Using a fixed seed with SecureRandom",
            "Specifying a fixed seed will cause the instance to return a predictable " +
                    "sequence of numbers. This may be useful for testing but it is not appropriate " +
                    "for secure use.",
            Category.SECURITY,
            9,
            Severity.WARNING,
            Implementation(SecureRandomDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}