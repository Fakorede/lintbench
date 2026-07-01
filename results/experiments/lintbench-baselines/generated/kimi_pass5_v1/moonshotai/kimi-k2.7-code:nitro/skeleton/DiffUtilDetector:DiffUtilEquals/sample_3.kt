package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
        private const val DIFF_UTIL_ITEM_CALLBACK =
            "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val LEGACY_DIFF_UTIL_ITEM_CALLBACK =
            "android.support.v7.util.DiffUtil.ItemCallback"
        private const val OBJECT_CLASS = "java.lang.Object"

        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to determine whether two items
                represent the same content. If the implementation compares items with identity
                equality (`==` in Java, `===` in Kotlin) or calls `equals` on a type that does
                not override `Object.equals`, the comparison will effectively be by object
                reference. This can cause `DiffUtil` to compute incorrect deltas and produce
                visual artifacts when the list is updated.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> =
        listOf(DIFF_UTIL_ITEM_CALLBACK, LEGACY_DIFF_UTIL_ITEM_CALLBACK)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
                method.accept(SuspiciousEqualityVisitor(context))
            }
        }
    }

    private fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        when (node.operator) {
            UastBinaryOperator.IDENTITY_EQUALS,
            UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                val left = node.leftOperand
                val right = node.rightOperand
                if (!isNullLiteral(left) && !isNullLiteral(right) &&
                    isReferenceExpression(left) && isReferenceExpression(right)
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious identity comparison in `areContentsTheSame`; " +
                            "compare item contents, not object identity.",
                    )
                }
            }
            UastBinaryOperator.EQUALS -> {
                val operator = node.resolveOperator()
                if (operator != null &&
                    operator.name == "equals" &&
                    operator.containingClass?.qualifiedName == OBJECT_CLASS
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious equality check in `areContentsTheSame`; " +
                            "the item type does not appear to override `equals()`.",
                    )
                }
            }
            else -> {}
        }
    }

    private fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val method = node.resolve()
        if (method != null &&
            method.name == "equals" &&
            method.containingClass?.qualifiedName == OBJECT_CLASS
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling `Object.equals` in `areContentsTheSame`; ensure the item type " +
                    "overrides `equals()` to compare contents.",
            )
        }
    }

    private inner class SuspiciousEqualityVisitor(
        private val context: JavaContext,
    ) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            visitBinaryExpression(context, node)
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            visitCallExpression(context, node)
            return super.visitCallExpression(node)
        }
    }

    private fun isReferenceExpression(expression: UExpression): Boolean {
        val type = expression.getExpressionType() ?: return false
        if (type is PsiPrimitiveType) return false
        val clazz = (type as? PsiClassType)?.resolve()
        return clazz == null || !clazz.isEnum
    }

    private fun isNullLiteral(expression: UExpression): Boolean =
        expression is ULiteralExpression && expression.value == null
}