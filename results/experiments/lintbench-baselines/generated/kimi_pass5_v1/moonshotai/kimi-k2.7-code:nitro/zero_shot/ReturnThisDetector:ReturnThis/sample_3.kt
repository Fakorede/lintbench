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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }
                if (node.uastBody == null) {
                    return
                }
                if (!hasReturnThisAnnotation(node) && !hasReturnThisSuperMethod(node, context)) {
                    return
                }
                if (!returnsThis(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method must return `this` because it is annotated with `@ReturnThis` (or overrides a method that is)"
                    )
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { it.qualifiedName?.isReturnThis() == true }
    }

    private fun hasReturnThisSuperMethod(method: UMethod, context: JavaContext): Boolean {
        return context.evaluator.findSuperMethods(method).any { hasReturnThisAnnotation(it) }
    }

    private fun returnsThis(method: UMethod): Boolean {
        val body = method.uastBody ?: return false

        val returns = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
            override fun visitMethod(node: UMethod): Boolean = true
            override fun visitClass(node: UClass): Boolean = true

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returns.add(node)
                return false
            }
        })

        if (returns.isEmpty()) {
            return false
        }
        return returns.all { it.returnExpression is UThisExpression }
    }

    private fun String?.isReturnThis(): Boolean {
        return this == "ReturnThis" || this?.substringAfterLast('.') == "ReturnThis"
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (including methods that override an annotated super method) \
                must return `this`. Returning anything else breaks the builder/fluent pattern that the annotation \
                is intended to enforce.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}