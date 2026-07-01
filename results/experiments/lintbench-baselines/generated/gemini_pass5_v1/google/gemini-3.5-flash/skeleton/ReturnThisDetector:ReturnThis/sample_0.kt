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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown
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
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String>? {
        return listOf("ReturnThis")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return false
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // No-op, checked dynamically via AST traversal in visitClass
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (hasReturnThisAnnotation(method)) {
                checkMethod(context, method)
            }
        }
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val expr = node.returnExpression?.skipParenthesizedExprDown()
        if (expr !is UThisExpression) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Method must return `this`"
            )
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        if (method.annotations.any { isReturnThis(it.qualifiedName) }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            val modifierList = superMethod.modifierList
            val psiAnnotations = modifierList.annotations
            if (psiAnnotations.any { isReturnThis(it.qualifiedName) }) {
                return true
            }
        }
        return false
    }

    private fun isReturnThis(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return qualifiedName == "ReturnThis" || qualifiedName.endsWith(".ReturnThis")
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        if (method.uastBody == null) return

        val returnExpressions = mutableListOf<UReturnExpression>()
        method.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                var parent = node.uastParent
                while (parent != null && parent !is UMethod) {
                    parent = parent.uastParent
                }
                if (parent == method) {
                    returnExpressions.add(node)
                }
                return super.visitReturnExpression(node)
            }
        })

        if (returnExpressions.isEmpty()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "Method must return `this`"
            )
        } else {
            for (ret in returnExpressions) {
                visitReturnExpression(context, ret)
            }
        }
    }
}