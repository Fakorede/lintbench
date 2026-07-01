package com.android.tools.lint.checks

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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement?>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return
                if (node.returnType == PsiType.VOID) return

                val psiMethod = node.javaPsi ?: return
                if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return

                val superMethods = psiMethod.findSuperMethods()
                val hasReturnThisAnnotation = superMethods.any { it.hasReturnThisAnnotation() }
                if (!hasReturnThisAnnotation) return

                val body = node.uastBody ?: return

                if (body is UThisExpression) {
                    return
                }

                if (body is UReturnExpression) {
                    checkReturnExpression(context, body)
                    return
                }

                if (body !is UBlockExpression) {
                    reportMustReturnThis(context, node)
                    return
                }

                var foundReturn = false
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        foundReturn = true
                        checkReturnExpression(context, node)
                        return super.visitReturnExpression(node)
                    }
                })

                if (!foundReturn) {
                    reportMustReturnThis(context, node)
                }
            }
        }
    }

    private fun checkReturnExpression(context: JavaContext, node: UReturnExpression) {
        val returnExpression = node.returnExpression
        if (returnExpression !is UThisExpression) {
            reportMustReturnThis(context, returnExpression ?: node)
        }
    }

    private fun reportMustReturnThis(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method must return `this`"
        )
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
        return annotations.any { it.isReturnThis() }
    }

    private fun PsiAnnotation.isReturnThis(): Boolean {
        val qName = qualifiedName
        return qName == "ReturnThis" || qName?.endsWith(".ReturnThis") == true
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods that override a method annotated with `@ReturnThis` must return `this`. \
                Returning any other value violates the contract promised by the annotation.
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