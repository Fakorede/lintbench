package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UElementHandler

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

                if (body is UBlockExpression) {
                    var allReturnThis = true
                    body.accept(object : AbstractUastVisitor() {
                        override fun visitReturnExpression(node: UReturnExpression): Boolean {
                            if (node.returnValue !is UThisExpression) {
                                allReturnThis = false
                            }
                            return super.visitReturnExpression(node)
                        }
                    })

                    if (!allReturnThis) {
                        report(context, node)
                    }
                } else if (body !is UThisExpression) {
                    report(context, node)
                }
            }
        }
    }

    private fun UMethod.shouldReturnThis(): Boolean {
        if (hasAnnotation(RETURN_THIS_ANNOTATION)) {
            return true
        }

        val superMethods = (this as? PsiMethod)?.findSuperMethods() ?: return false
        return superMethods.any { it.hasAnnotation(RETURN_THIS_ANNOTATION) }
    }

    private fun PsiMethod.hasAnnotation(fqn: String): Boolean {
        return modifierList?.findAnnotation(fqn) != null
    }

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