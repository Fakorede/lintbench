package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) return
                if (node.body == null) return

                val evaluator = context.evaluator
                val requiresReturnThis = hasReturnThisAnnotation(evaluator, node)
                        || hasReturnThisAnnotationInHierarchy(evaluator, node)

                if (!requiresReturnThis) return

                if (!returnsThis(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method must return `this` because it is (or overrides a method) annotated with @ReturnThis"
                    )
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(evaluator: JavaEvaluator, method: UMethod): Boolean {
        return evaluator.getAllAnnotations(method, false).any { it.isReturnThis() }
    }

    private fun hasReturnThisAnnotationInHierarchy(evaluator: JavaEvaluator, method: UMethod): Boolean {
        var superMethod: com.intellij.psi.PsiMethod? = evaluator.getSuperMethod(method)
        while (superMethod != null) {
            if (evaluator.getAllAnnotations(superMethod, false).any { it.isReturnThis() }) {
                return true
            }
            superMethod = evaluator.getSuperMethod(superMethod)
        }
        return false
    }

    private fun UAnnotation.isReturnThis(): Boolean {
        val name = qualifiedName
        return name == "ReturnThis" || name?.substringAfterLast('.') == "ReturnThis"
    }

    private fun returnsThis(method: UMethod): Boolean {
        val body = method.body ?: return false

        // Kotlin single-expression functions, e.g. `fun foo() = this`
        if (body !is UBlockExpression) {
            return body.isThisReference()
        }

        val returns = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returns.add(node)
                return super.visitReturnExpression(node)
            }
        })

        if (returns.isEmpty()) return false
        return returns.all { it.returnExpression.isThisReference() }
    }

    private fun UExpression?.isThisReference(): Boolean {
        return this is UThisExpression
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` must return `this`. This also applies to methods
                that override a super method annotated with `@ReturnThis`.
            """.trimIndent(),
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