package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.*
import org.jetbrains.uast.*

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_SIMPLE_NAME = "ReturnThis"

        private val ANNOTATION_NAMES = listOf(
            "androidx.annotation.ReturnThis",
            "android.support.annotation.ReturnThis",
            ANNOTATION_SIMPLE_NAME
        )

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis`, or methods that override such a method,
                must return `this`. Returning any other value breaks the expected contract,
                such as a builder/fluent API.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    private val methodsRequiringThis = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String>? = ANNOTATION_NAMES

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String
    ) {
        val method = element as? UMethod ?: return
        val psiMethod = method.javaPsi ?: return
        methodsRequiringThis.add(psiMethod)
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType(UMethod::class.java, true) ?: return
        val psiMethod = method.javaPsi ?: return
        if (psiMethod !in methodsRequiringThis) return

        var returned = node.returnExpression
        while (returned is UParenthesizedExpression) {
            returned = returned.expression
        }

        if (returned is UThisExpression) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method annotated with @ReturnThis must return `this`"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            val psiMethod = method.javaPsi ?: continue
            if (hasReturnThisAnnotation(method) || hasReturnThisSuperMethod(psiMethod)) {
                methodsRequiringThis.add(psiMethod)
            }
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        for (annotation in method.uAnnotations) {
            val name = annotation.qualifiedName ?: continue
            if (isReturnThis(name)) return true
        }
        return false
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        val modifierList = method.modifierList ?: return false
        for (annotation in modifierList.annotations) {
            val name = annotation.qualifiedName ?: continue
            if (isReturnThis(name)) return true
        }
        return false
    }

    private fun hasReturnThisSuperMethod(method: PsiMethod): Boolean {
        val supers = method.findSuperMethods()
        if (supers.isEmpty()) return false
        for (superMethod in supers) {
            if (hasReturnThisAnnotation(superMethod)) return true
            if (hasReturnThisSuperMethod(superMethod)) return true
        }
        return false
    }

    private fun isReturnThis(name: String): Boolean =
        name == ANNOTATION_SIMPLE_NAME ||
            name == "androidx.annotation.ReturnThis" ||
            name == "android.support.annotation.ReturnThis"
}