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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiType
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.accept

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return
                if (node.returnType == null || node.returnType == PsiType.VOID) return

                val annotatedHere = node.annotations.any { it.isReturnThis() }
                val annotatedInSuper = node.findSuperMethods(true).any { superMethod ->
                    superMethod.annotations.any { it.isReturnThis() }
                }

                if (!annotatedHere && !annotatedInSuper) return

                val body = node.uastBody ?: return
                if (!body.returnsThis()) {
                    context.report(
                        ISSUE,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }

    private fun UAnnotation.isReturnThis(): Boolean {
        val name = qualifiedName
        return name?.endsWith(".ReturnThis") == true || name == "ReturnThis"
    }

    private fun PsiAnnotation.isReturnThis(): Boolean {
        val name = qualifiedName
        return name?.endsWith(".ReturnThis") == true || name == "ReturnThis"
    }

    private fun UExpression.returnsThis(): Boolean {
        val returns = mutableListOf<UReturnExpression>()
        accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returns.add(node)
                return super.visitReturnExpression(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                // Don't inspect returns inside nested lambdas.
                return true
            }

            override fun visitClass(node: UClass): Boolean {
                // Don't inspect returns inside nested/local classes.
                return true
            }
        })
        return if (returns.isNotEmpty()) {
            returns.all { it.returnExpression is UThisExpression }
        } else {
            this is UThisExpression
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods that are annotated with `@ReturnThis` or override a method annotated
                with `@ReturnThis` must return `this`. This is commonly required for builder
                or fluent API methods.
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