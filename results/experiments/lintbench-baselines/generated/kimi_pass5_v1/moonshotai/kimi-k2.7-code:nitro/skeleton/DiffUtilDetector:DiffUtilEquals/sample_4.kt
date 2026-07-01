package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
        private const val EQUALS_METHOD = "equals"
        private const val JAVA_OBJECT = "java.lang.Object"
        private const val KOTLIN_ANY = "kotlin.Any"

        private val APPLICABLE_CLASSES = listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback"
        )

        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. It should compare the actual contents of two items.

                Using identity equality (`==` in Java, `===` in Kotlin) compares object references, not contents, which can cause incorrect diff results and visual artifacts. Similarly, calling `.equals()` on a type that does not override `Object.equals()`/`Any.equals()` is equivalent to identity comparison and is also suspicious.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    override fun applicableSuperClasses(): List<String> = APPLICABLE_CLASSES

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == ARE_CONTENTS_THE_SAME && method.uastParameters.size == 2) {
                inspectMethod(context, method)
            }
        }
    }

    private fun inspectMethod(context: JavaContext, method: UMethod) {
        method.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                checkBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    private fun checkBinaryExpression(context: JavaContext, expression: UBinaryExpression) {
        if (expression.leftOperand.isNullLiteral() || expression.rightOperand.isNullLiteral()) {
            return
        }

        val operator = expression.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS ||
            operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            report(
                context,
                expression,
                "Identity equality should not be used in areContentsTheSame; compare item contents instead."
            )
            return
        }

        // In Java, == and != on reference types are identity comparisons.
        if (expression.sourcePsi is PsiBinaryExpression &&
            (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS)
        ) {
            val leftType = expression.leftOperand.expressionType
            val rightType = expression.rightOperand.expressionType
            if (leftType.isReferenceType() && rightType.isReferenceType()) {
                report(
                    context,
                    expression,
                    "Java ==/!= compares object identity; use .equals() to compare item contents in areContentsTheSame."
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, expression: UCallExpression) {
        if (expression.methodName != EQUALS_METHOD || expression.valueArgumentCount != 1) {
            return
        }

        val resolved = expression.resolve() as? PsiMethod ?: return
        val containingClass = resolved.containingClass ?: return
        val className = containingClass.qualifiedName
        if (className == JAVA_OBJECT || className == KOTLIN_ANY) {
            report(
                context,
                expression,
                "Calling Object/Any.equals() does not compare contents; ensure the item type overrides equals() or compare fields explicitly."
            )
        }
    }

    private fun UExpression.isNullLiteral(): Boolean =
        this is ULiteralExpression && value == null

    private fun PsiType?.isReferenceType(): Boolean {
        if (this == null || this.isPrimitiveOrBoxed()) return false
        if (this is PsiClassType && resolve()?.isEnum == true) return false
        return true
    }

    private fun PsiType.isPrimitiveOrBoxed(): Boolean {
        return when (canonicalText) {
            "boolean", "byte", "char", "short", "int", "long", "float", "double",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double" -> true
            else -> false
        }
    }

    private fun report(context: JavaContext, node: UExpression, message: String) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = message
        )
    }
}