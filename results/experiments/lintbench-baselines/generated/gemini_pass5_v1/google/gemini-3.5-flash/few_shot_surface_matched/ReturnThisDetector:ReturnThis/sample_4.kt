package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    private val methodsThatMustReturnThis = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> {
        return listOf("ReturnThis", "com.android.tools.lint.checks.ReturnThis")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.DEFINITION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod,
        referenced: PsiElement?,
        annotations: List<UAnnotation>
    ) {
        val uMethod = usage.getParentOfType<UMethod>(true) ?: return
        methodsThatMustReturnThis.add(uMethod.javaPsi)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReturnExpression::class.java, UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReturnExpression(node: UReturnExpression) {
                this@ReturnThisDetector.visitReturnExpression(context, node)
            }

            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }
        }
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType<UMethod>(true) ?: return
        val psiMethod = method.javaPsi
        if (methodsThatMustReturnThis.contains(psiMethod) || psiMethod.hasReturnThisAnnotation() || psiMethod.findSuperMethods().any { it.hasReturnThisAnnotation() }) {
            val returnExpr = node.returnExpression
            if (returnExpr !is UThisExpression) {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Method annotated with @ReturnThis must return 'this'"
                    )
                )
            }
        }
    }

    fun visitClass(context: JavaContext, node: UClass) {
        // Required override placeholder
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
        return annotations.any {
            val qualifiedName = it.qualifiedName
            qualifiedName == "ReturnThis" || qualifiedName?.endsWith(".ReturnThis") == true
        }
    }
}