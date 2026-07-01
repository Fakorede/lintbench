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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return
                if (node.returnType == null || node.returnType == PsiType.VOID) return

                val hasReturnThisAnnotation = node.hasReturnThisAnnotation() ||
                    node.findSuperMethods(true).any { it.hasReturnThisAnnotation() }

                if (!hasReturnThisAnnotation) return

                val body = node.uastBody ?: return
                if (!body.returnsThis()) {
                    context.report(
                        ISSUE,
                        context.getNameLocation(node),
                        "Method overrides a method annotated @ReturnThis and must return `this`"
                    )
                }
            }
        }

    private fun UMethod.hasReturnThisAnnotation(): Boolean =
        annotations.any { it.qualifiedName?.endsWith(".ReturnThis") == true || it.name == "ReturnThis" }

    private fun org.jetbrains.uast.UExpression.returnsThis(): Boolean =
        when (this) {
            is UReturnExpression -> returnExpression is UThisExpression
            is UBlockExpression -> {
                val returns = expressions.filterIsInstance<UReturnExpression>()
                if (returns.isNotEmpty()) {
                    returns.all { it.returnExpression is UThisExpression }
                } else {
                    expressions.lastOrNull() is UThisExpression
                }
            }
            else -> false
        }

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods that override a method annotated with `@ReturnThis` must return `this`.
                This is commonly required for builder or fluent API methods.
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