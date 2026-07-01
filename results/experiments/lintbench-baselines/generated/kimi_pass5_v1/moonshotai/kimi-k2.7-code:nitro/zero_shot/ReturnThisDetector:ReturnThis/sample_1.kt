package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor || node.returnType == PsiType.VOID) {
                    return
                }

                if (!hasReturnThisAnnotation(node, context.evaluator)) {
                    return
                }

                val body = node.uastBody ?: return

                if (body !is UBlockExpression) {
                    if (body !is UThisExpression) {
                        reportNonThisReturn(context, body)
                    }
                    return
                }

                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        if (node.returnExpression !is UThisExpression) {
                            reportNonThisReturn(context, node)
                        }
                        return super.visitReturnExpression(node)
                    }
                })
            }
        }
    }

    private fun reportNonThisReturn(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method annotated with @ReturnThis must return `this`"
        )
    }

    private fun hasReturnThisAnnotation(method: PsiMethod, evaluator: JavaEvaluator): Boolean {
        if (method.hasReturnThisAnnotation()) return true
        val containingClass = method.containingClass ?: return false
        return evaluator.getSuperMethods(method, containingClass).any { it.hasReturnThisAnnotation() }
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
        return annotations.any { annotation ->
            annotation.qualifiedName?.substringAfterLast('.') == RETURN_THIS_ANNOTATION
        }
    }

    companion object {
        private const val RETURN_THIS_ANNOTATION = "ReturnThis"

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method should return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (or overriding a method
                annotated with `@ReturnThis`) must return `this`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}