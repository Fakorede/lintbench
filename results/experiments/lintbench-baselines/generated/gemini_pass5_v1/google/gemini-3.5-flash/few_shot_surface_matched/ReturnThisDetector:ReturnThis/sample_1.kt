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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    private val checkedMethods = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> = listOf(
        "ReturnThis",
        "com.example.ReturnThis"
    )

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_ANNOTATION ||
               type == AnnotationUsageType.METHOD_OVERRIDE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation
    ) {
        val method = usage.getParentOfType<UMethod>(true) ?: return
        checkMethod(context, method)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java, UReturnExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }

            override fun visitReturnExpression(node: UReturnExpression) {
                this@ReturnThisDetector.visitReturnExpression(context, node)
            }
        }

    fun visitClass(context: JavaContext, node: UClass) {
        for (method in node.methods) {
            if (hasReturnThisAnnotation(method)) {
                checkMethod(context, method)
            }
        }
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType<UMethod>(true) ?: return
        if (hasReturnThisAnnotation(method)) {
            if (!isThisExpression(node.returnExpression)) {
                reportError(context, node, "Method annotated with @ReturnThis must return 'this'")
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val psi = method.javaPsi
        if (!checkedMethods.add(psi)) return

        val body = method.uastBody ?: return

        val returns = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                var parent = node.uastParent
                while (parent != null && parent != body) {
                    if (parent is UMethod || parent is ULambdaExpression) {
                        return true
                    }
                    parent = parent.uastParent
                }
                returns.add(node)
                return super.visitReturnExpression(node)
            }
        })

        if (returns.isEmpty()) {
            if (body !is UBlockExpression) {
                if (!isThisExpression(body)) {
                    reportError(context, body, "Method annotated with @ReturnThis must return 'this'")
                }
            } else {
                val location = context.getNameLocation(method)
                context.report(
                    Incident(ISSUE, method, location, "Method annotated with @ReturnThis must return 'this'")
                )
            }
        } else {
            for (ret in returns) {
                if (!isThisExpression(ret.returnExpression)) {
                    reportError(context, ret, "Method annotated with @ReturnThis must return 'this'")
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        if (method.hasAnnotation("ReturnThis") || 
            method.annotations.any { it.qualifiedName?.endsWith(".ReturnThis") == true }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (superMethod.hasAnnotation("ReturnThis") || 
                superMethod.annotations.any { it.qualifiedName?.endsWith(".ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        if (expression == null) return false
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        if (current is UThisExpression) return true
        if (current is UQualifiedReferenceExpression) {
            return current.selector is UThisExpression
        }
        return false
    }

    private fun reportError(context: JavaContext, node: UElement, message: String) {
        val location = context.getLocation(node)
        context.report(Incident(ISSUE, node, location, message))
    }
}