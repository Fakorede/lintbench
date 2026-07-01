package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!node.shouldReturnThis()) {
                    return
                }

                val body = node.uastBody ?: return

                var hasReturn = false
                var allReturnThis = true
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        hasReturn = true
                        val value = node.returnValue
                        if (value !is UThisExpression) {
                            allReturnThis = false
                        }
                        return super.visitReturnExpression(node)
                    }
                })

                if (!allReturnThis || !hasReturn) {
                    report(context, node)
                }
            }
        }
    }

    private fun UMethod.shouldReturnThis(): Boolean {
        if (uAnnotations.any { it.isReturnThis }) {
            return true
        }

        val psi = javaPsi as? PsiMethod ?: return false
        if (psi.isReturnThis) {
            return true
        }

        return psi.findSuperMethods().any { it.isReturnThis }
    }

    private val UAnnotation.isReturnThis: Boolean
        get() = qualifiedName == RETURN_THIS_ANNOTATION ||
                qualifiedName?.endsWith(".$RETURN_THIS_ANNOTATION") == true

    private val PsiMethod.isReturnThis: Boolean
        get() = modifierList?.annotations?.any { annotation ->
            annotation.qualifiedName == RETURN_THIS_ANNOTATION ||
                    annotation.qualifiedName?.endsWith(".$RETURN_THIS_ANNOTATION") == true
        } == true

    private fun report(context: JavaContext, node: UMethod) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method overriding or annotated with @ReturnThis must return `this`"
        )
    }

    companion object {
        private const val RETURN_THIS_ANNOTATION = "ReturnThis"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (or that override a method annotated with it) \
                must return `this` to preserve the builder/fluent API contract.
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