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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_NAME = "ReturnThis"
        private const val MESSAGE = "Method must return `this`"

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with @ReturnThis, as well as methods that override such methods,
                must return `this`. Returning any other value breaks the expected fluent builder-style
                chaining behavior.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_NAME)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_DECLARATION ||
            type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val method = element as? UMethod ?: return
        val psiMethod = method.javaPsi
        if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT) ||
            psiMethod.hasModifierProperty(PsiModifier.NATIVE)
        ) {
            return
        }
        if (!method.returnsThis()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                MESSAGE,
            )
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Checks are performed directly in visitAnnotationUsage.
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Inherited cases are handled via AnnotationUsageType.METHOD_OVERRIDE.
    }

    private fun UMethod.returnsThis(): Boolean {
        val body = uastBody ?: return false
        var foundReturn = false
        var allReturnThis = true
        body.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                return true // Do not descend into nested/local methods.
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                if (node.returnIdentifier != null) {
                    // Labeled return; skip because it returns from a lambda, not this method.
                    return super.visitReturnExpression(node)
                }
                foundReturn = true
                if (node.returnExpression !is UThisExpression) {
                    allReturnThis = false
                }
                return super.visitReturnExpression(node)
            }
        })
        return foundReturn && allReturnThis
    }

    private val PsiAnnotation.isReturnThis: Boolean
        get() = qualifiedName?.substringAfterLast('.') == ANNOTATION_NAME
}