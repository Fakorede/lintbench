package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://goo.gle/SecureRandom")
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
            val firstArg = node.valueArguments[0]
            if (isFixedSeed(context, firstArg)) {
                report(context, node)
            }
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
        val evaluator = context.evaluator
        val containingClass = method.containingClass
        if (containingClass != null && evaluator.inheritsFrom(containingClass, "java.security.SecureRandom", false)) {
            if (node.valueArgumentCount > 0) {
                val firstArg = node.valueArguments[0]
                if (isFixedSeed(context, firstArg)) {
                    report(context, node)
                }
            }
        }
    }

    private fun isFixedSeed(context: JavaContext, expression: UExpression): Boolean {
        val constant = ConstantEvaluator().evaluate(expression)
        if (constant != null) {
            return true
        }
        if (expression is ULiteralExpression) {
            return true
        }
        if (expression is UCallExpression) {
            val kind = expression.kind
            if (kind == UastCallKind.ARRAY_INITIALIZER) {
                val initializers = expression.valueArguments
                if (initializers.isNotEmpty()) {
                    return initializers.all { isFixedSeed(context, it) }
                }
            }
            val name = expression.methodName
            if (name != null && (name.endsWith("ArrayOf") || name == "arrayOf")) {
                return expression.valueArguments.all { isFixedSeed(context, it) }
            }
        }
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiLocalVariable || resolved is PsiField) {
                val uVar = context.uastContext.toUElement(resolved, UVariable::class.java)
                val initializer = uVar?.uastInitializer
                if (initializer != null) {
                    return isFixedSeed(context, initializer)
                }
            }
        }
        return false
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not call `setSeed` or use a seeded constructor with a fixed seed on `SecureRandom`"
        )
    }
}