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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable  sequence of numbers. This may be useful for testing but it is not appropriate  for secure use.
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
        if (containingClass != null) {
            val receiverClass = (node.receiverType as? PsiClassType)?.resolve()
            val isSecureRandom = evaluator.inheritsFrom(containingClass, "java.security.SecureRandom", false) ||
                    (evaluator.inheritsFrom(containingClass, "java.util.Random", false) && 
                     evaluator.inheritsFrom(receiverClass, "java.security.SecureRandom", false))
            if (isSecureRandom) {
                if (node.valueArgumentCount > 0) {
                    val firstArg = node.valueArguments[0]
                    if (isFixedSeed(context, firstArg)) {
                        report(context, node)
                    }
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
            val methodName = expression.methodName
            if (methodName == "getBytes" || methodName == "toByteArray") {
                val receiver = expression.receiver
                if (receiver != null && isFixedSeed(context, receiver)) {
                    return true
                }
            }
            
            val method = expression.resolve()
            if (method != null && method.isConstructor) {
                val containingClass = method.containingClass
                if (containingClass?.qualifiedName == "java.math.BigInteger") {
                    return expression.valueArguments.all { isFixedSeed(context, it) }
                }
            }

            val kindName = expression.kind.name
            if (kindName.contains("array", ignoreCase = true)) {
                val initializers = expression.valueArguments
                if (initializers.isNotEmpty()) {
                    return initializers.all { isFixedSeed(context, it) }
                }
            }
            if (methodName != null && (methodName.endsWith("ArrayOf") || methodName == "arrayOf")) {
                return expression.valueArguments.all { isFixedSeed(context, it) }
            }
        }
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiLocalVariable || resolved is PsiField) {
                val uVar = resolved.toUElement(UVariable::class.java)
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