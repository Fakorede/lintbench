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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastUtils

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    private val annotatedMethods = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> {
        return listOf(
            "ReturnThis",
            "com.android.annotations.ReturnThis",
            "android.annotation.ReturnThis"
        )
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return true
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        member: PsiModifierListOwner,
        referenced: PsiElement,
        annotations: List<UAnnotation>
    ) {
        if (member is PsiMethod) {
            annotatedMethods.add(member)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReturnExpression::class.java, UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }

            override fun visitReturnExpression(node: UReturnExpression) {
                this@ReturnThisDetector.visitReturnExpression(context, node)
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (uMethod in declaration.methods) {
            val psiMethod = uMethod.javaPsi
            if (hasReturnThis(psiMethod, context)) {
                annotatedMethods.add(psiMethod)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = UastUtils.getParentOfType(node, UMethod::class.java) ?: return
        val psiMethod = method.javaPsi

        if (annotatedMethods.contains(psiMethod) || hasReturnThis(psiMethod, context)) {
            var returnExpr = node.returnExpression
            while (returnExpr is UParenthesizedExpression) {
                returnExpr = returnExpr.expression
            }

            val returnsThis = returnExpr is UThisExpression ||
                    (returnExpr != null && returnExpr.sourcePsi?.text?.trim() == "this")

            if (!returnsThis) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Method must return `this`"
                )
            }
        }
    }

    private fun hasReturnThis(method: PsiMethod, context: JavaContext): Boolean {
        val evaluator = context.evaluator
        if (evaluator.getAllAnnotations(method, false).any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (evaluator.getAllAnnotations(superMethod, false).any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }
}