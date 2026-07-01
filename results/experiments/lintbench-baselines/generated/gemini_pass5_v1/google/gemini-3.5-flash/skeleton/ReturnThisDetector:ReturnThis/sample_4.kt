package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithTypeCast
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding such methods) must return `this`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }
        }
    }

    override fun applicableAnnotations(): List<String>? {
        return listOf("ReturnThis")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return false
    }

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // Handled via visitClass
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val returnExpr = node.returnExpression
        if (returnExpr == null || !isThisExpression(returnExpr)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Method must return `this`"
            )
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (isReturnThisMethod(method)) {
                method.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        visitReturnExpression(context, node)
                        return super.visitReturnExpression(node)
                    }
                })
            }
        }
    }

    private fun isReturnThisMethod(method: UMethod): Boolean {
        if (hasReturnThisAnnotation(method)) return true
        for (superMethod in method.findSuperMethods()) {
            if (hasReturnThisAnnotation(superMethod)) return true
        }
        return false
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        return method.uAnnotations.any { isReturnThisAnnotation(it.qualifiedName) }
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { isReturnThisAnnotation(it.qualifiedName) }
    }

    private fun isReturnThisAnnotation(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return qualifiedName == "ReturnThis" || qualifiedName.endsWith(".ReturnThis")
    }

    private fun isThisExpression(expression: UElement?): Boolean {
        var curr = expression
        while (curr is UParenthesizedExpression) {
            curr = curr.expression
        }
        if (curr is UBinaryExpressionWithTypeCast) {
            curr = curr.operand
        }
        return curr is UThisExpression
    }
}