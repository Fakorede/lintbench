package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UNewArrayExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement

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
            androidSpecific = true,
            reference = "https://goo.gle/SecureRandom",
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
        if (evaluator.isMemberInSubclassOf(method, "java.security.SecureRandom", false)) {
            if (node.valueArgumentCount > 0) {
                val firstArg = node.valueArguments[0]
                if (isFixedSeed(context, firstArg)) {
                    report(context, node)
                }
            }
        }
    }

    private fun isFixedSeed(context: JavaContext, expression: UExpression): Boolean {
        val constant = context.constantEvaluator.evaluate(expression)
        if (constant != null) {
            return true
        }
        if (expression is ULiteralExpression) {
            return true
        }
        if (expression is UNewArrayExpression) {
            val initializers = expression.initializers
            if (initializers.isNotEmpty()) {
                return initializers.all { isFixedSeed(context, it) }
            }
        }
        if (expression is UCallExpression) {
            val name = expression.methodName
            if (name != null && name.endsWith("ArrayOf")) {
                return expression.valueArguments.all { isFixedSeed(context, it) }
            }
        }
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiLocalVariable || resolved is PsiField) {
                val uVar = (resolved as PsiVariable).toUElement() as? UVariable
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