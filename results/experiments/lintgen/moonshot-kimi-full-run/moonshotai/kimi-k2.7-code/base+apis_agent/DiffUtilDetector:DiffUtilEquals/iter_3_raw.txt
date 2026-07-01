package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality check",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Using identity
                equality (`==` in Java or `===` in Kotlin) or calling `equals` on a type that
                does not override it can produce incorrect diff results and visual artifacts.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val METHOD_NAME = "areContentsTheSame"
        private const val EQUALS_METHOD = "equals"
        private const val JAVA_OBJECT = "java.lang.Object"
        private const val KOTLIN_ANY = "kotlin.Any"
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "androidx.recyclerview.widget.DiffUtil.ItemCallback",
        "android.support.v7.util.DiffUtil.Callback",
        "android.support.v7.util.DiffUtil.ItemCallback"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods.filterIsInstance<UMethod>()) {
            if (method.name == METHOD_NAME) {
                method.uastBody?.accept(EqualityVisitor(context))
            }
        }
    }

    private class EqualityVisitor(
        private val context: JavaContext
    ) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
                node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                val leftType = node.leftOperand.getExpressionType()
                val rightType = node.rightOperand.getExpressionType()
                if (leftType != null && rightType != null &&
                    leftType !is PsiPrimitiveType && rightType !is PsiPrimitiveType
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious identity equality check in `areContentsTheSame`; use structural equality instead."
                    )
                }
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == EQUALS_METHOD && node.valueArgumentCount == 1) {
                val receiverType = node.receiver?.getExpressionType()
                if (receiverType != null && !isEnumOrPrimitive(receiverType)) {
                    val method: PsiMethod? = node.resolve()
                    val containingClass = method?.containingClass
                    val qualifiedName = containingClass?.qualifiedName
                    if (qualifiedName == JAVA_OBJECT || qualifiedName == KOTLIN_ANY) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Suspicious `equals` call in `areContentsTheSame`; the receiver type does not override `equals`."
                        )
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun isEnumOrPrimitive(type: PsiType): Boolean {
            if (type is PsiPrimitiveType) return true
            val cls = context.evaluator.getTypeClass(type) ?: return false
            return cls.isEnum
        }
    }
}