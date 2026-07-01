package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionKind
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod

class DiffUtilDetector : Detector(), UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("areContentsTheSame")

    override fun visitMethod(context: JavaContext, node: UMethod) {
        val superMethods = node.javaPsi?.findSuperMethods() ?: return
        val isDiffUtilCallback = superMethods.any { superMethod ->
            val qName = superMethod.containingClass?.qualifiedName
            (qName == "androidx.recyclerview.widget.DiffUtil.Callback" ||
             qName == "androidx.recyclerview.widget.DiffUtil.ItemCallback" ||
             qName == "android.support.v7.util.DiffUtil.Callback") &&
            superMethod.name == "areContentsTheSame"
        }

        if (!isDiffUtilCallback) return

        val body = node.uastBody ?: return
        body.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(expression: UBinaryExpression): Boolean {
                when (expression.operator) {
                    UBinaryExpressionKind.IDENTITY_EQUALS -> {
                        context.report(
                            ISSUE,
                            expression,
                            context.getLocation(expression),
                            "Using identity equals in `areContentsTheSame`; use structural equals instead"
                        )
                    }
                    UBinaryExpressionKind.EQUALS -> {
                        val leftType = expression.leftOperand.getExpressionType()
                        val rightType = expression.rightOperand.getExpressionType()
                        if (!hasCustomEquals(context, leftType) || !hasCustomEquals(context, rightType)) {
                            context.report(
                                ISSUE,
                                expression,
                                context.getLocation(expression),
                                "Calling equals on a class that does not override it; unexpected diff behavior may occur"
                            )
                        }
                    }
                    else -> {}
                }
                return super.visitBinaryExpression(expression)
            }

            override fun visitCallExpression(expression: UCallExpression): Boolean {
                if (expression.methodName == "equals" && expression.valueArgumentCount == 1) {
                    val receiverType = expression.receiver?.getExpressionType()
                    if (!hasCustomEquals(context, receiverType)) {
                        context.report(
                            ISSUE,
                            expression,
                            context.getLocation(expression),
                            "Calling equals on a class that does not override it; unexpected diff behavior may occur"
                        )
                    }
                }
                return super.visitCallExpression(expression)
            }
        })
    }

    private fun hasCustomEquals(context: JavaContext, type: PsiType?): Boolean {
        if (type == null) return true
        val psiClass = (type as? PsiClassType)?.resolve() ?: return true
        if (psiClass.qualifiedName == "java.lang.Object") return false
        return psiClass.findMethodsByName("equals", true).any { method ->
            method.parameterList.parametersCount == 1 &&
            method.containingClass?.qualifiedName != "java.lang.Object"
        }
    }
}