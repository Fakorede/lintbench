package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "ReturnThis"

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return this",
            explanation = """
                Methods annotated with @ReturnThis, or methods that override a @ReturnThis-annotated
                method, must return `this` in order to preserve the expected API contract.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    private val requiredMethods = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allParameterAnnotations: List<UAnnotation>
    ) {
        if (type != AnnotationUsageType.METHOD_OVERRIDE) return
        val overriding = usage as? UMethod ?: return
        overriding.resolve()?.let { requiredMethods.add(it) }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType(UMethod::class.java, true)?.resolve() ?: return
        if (!requiresReturnThis(method, context) && method !in requiredMethods) return

        val returnValue = node.returnExpression
        if (returnValue is UThisExpression) return

        val location = context.getLocation(node)
        val message = "This method must return `this`"
        context.report(Incident(ISSUE, node, location, message))
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        requiredMethods.clear()
    }

    private fun requiresReturnThis(method: PsiMethod, context: JavaContext): Boolean {
        var current: PsiMethod? = method
        while (current != null) {
            if (hasReturnThisAnnotation(current)) return true
            current = context.evaluator.getSuperMethod(current)
        }
        return false
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean =
        method.annotations.any { annotation ->
            val name = annotation.qualifiedName
            name == RETURN_THIS_ANNOTATION || name?.endsWith(".$RETURN_THIS_ANNOTATION") == true
        }
}