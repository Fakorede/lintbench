package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION = "ReturnThis"

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods that are annotated with @ReturnThis, or that override a method annotated with @ReturnThis, must return `this` in every return statement.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private val candidateMethods = mutableSetOf<UMethod>()

    override fun beforeCheckEachFile(context: Context) {
        candidateMethods.clear()
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UClass::class.java, UReturnExpression::class.java)

    override fun applicableAnnotations(): List<String>? = listOf(ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allAttributeValues: List<org.jetbrains.uast.UNamedExpression>,
        signature: String?,
    ) {
        val overridingMethod = element as? UMethod ?: return
        candidateMethods.add(overridingMethod)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.mustReturnThis(context)) {
                candidateMethods.add(method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, expression: UReturnExpression) {
        val method = expression.enclosingMethod() ?: return
        if (!method.mustReturnThis(context) && method !in candidateMethods) {
            return
        }

        if (!isThisReference(expression.returnExpression)) {
            context.report(
                ISSUE,
                expression,
                context.getLocation(expression),
                "This method must return `this` because it is annotated with or overrides a @ReturnThis method",
            )
        }
    }

    private fun UMethod.mustReturnThis(context: JavaContext): Boolean {
        if (hasReturnThisAnnotation()) return true
        val psi = javaPsi ?: return false
        val superMethod = context.evaluator.getSuperMethod(psi) ?: return false
        return superMethod.hasReturnThisAnnotation()
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean =
        hasAnnotation(ANNOTATION)

    private fun UMethod.hasReturnThisAnnotation(): Boolean =
        uAnnotations.any {
            it.qualifiedName == ANNOTATION ||
                it.qualifiedName?.substringAfterLast('.') == ANNOTATION
        }

    private fun UElement.enclosingMethod(): UMethod? {
        var node: UElement? = this
        while (node != null) {
            if (node is UMethod) return node
            node = node.uastParent
        }
        return null
    }

    private fun isThisReference(expression: UExpression?): Boolean {
        if (expression == null) return false
        return when (expression) {
            is UThisExpression -> true
            is UParenthesizedExpression -> isThisReference(expression.expression)
            else -> false
        }
    }
}