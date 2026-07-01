package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) return
            if (node.hasModifierProperty(PsiModifier.ABSTRACT)) return
            if (node.hasModifierProperty(PsiModifier.NATIVE)) return

            val superMethods = node.findSuperMethods()
            val annotated = hasReturnThisAnnotation(node) ||
                    superMethods.any { hasReturnThisAnnotation(it) }
            if (!annotated) return

            if (!returnsThis(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Method must return `this` because it overrides or is annotated with @ReturnThis"
                )
            }
        }
    }

    private fun hasReturnThisAnnotation(owner: PsiModifierListOwner): Boolean {
        return owner.annotations.any {
            val qName = it.qualifiedName
            qName == "ReturnThis" || qName?.endsWith(".ReturnThis") == true
        }
    }

    private fun returnsThis(method: UMethod): Boolean {
        val body = method.uastBody ?: return false

        val visitor = object : AbstractUastVisitor() {
            var foundReturn = false
            var allReturnThis = true

            override fun visitMethod(node: UMethod): Boolean = true
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                foundReturn = true
                val value = node.returnExpression
                if (value != null && value !is UThisExpression) {
                    allReturnThis = false
                }
                return super.visitReturnExpression(node)
            }
        }

        body.accept(visitor)
        return visitor.foundReturn && visitor.allReturnThis
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method should return `this`",
            explanation = """
                Methods annotated with `@ReturnThis`, or methods that override a method annotated \
                with `@ReturnThis`, must return `this` so that callers can rely on method chaining.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}