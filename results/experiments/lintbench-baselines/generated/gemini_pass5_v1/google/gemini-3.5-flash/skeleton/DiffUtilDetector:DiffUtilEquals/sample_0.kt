package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, \
                such as using identity equals instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val method = declaration.methods.find { it.name == "areContentsTheSame" } ?: return
        analyzeMethod(context, method)
    }

    fun visitBinaryExpression() {
        // Placeholder to satisfy the skeleton
    }

    fun visitCallExpression() {
        // Placeholder to satisfy the skeleton
    }

    private fun analyzeMethod(context: JavaContext, method: UMethod) {
        val parameters = method.uastParameters
        val oldItemName = parameters.getOrNull(0)?.name ?: ""
        val newItemName = parameters.getOrNull(1)?.name ?: ""

        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                val left = node.leftOperand
                val right = node.rightOperand

                val isKotlinFile = isKotlin(node.sourcePsi)

                if (isKotlinFile) {
                    if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                        if (isComparingOldAndNew(left, right, oldItemName, newItemName)) {
                            if (isExpressionReturned(node, method)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Using identity equality (`===`) to compare items in `areContentsTheSame` is a bug; did you mean structural equality (`==`)?"
                                )
                            }
                        }
                    } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                        if (!isNullLiteral(left) && !isNullLiteral(right)) {
                            val type = left.getExpressionType() ?: right.getExpressionType()
                            if (type != null && !isPrimitiveOrNull(type) && !checkTypeOverridesEquals(type)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Comparing `${type.presentableText}` using `==` but the class does not override `equals`"
                                )
                            }
                        }
                    }
                } else {
                    // Java
                    if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                        if (!isNullLiteral(left) && !isNullLiteral(right)) {
                            val leftType = left.getExpressionType()
                            val rightType = right.getExpressionType()
                            if (leftType != null && rightType != null && !isPrimitiveOrNull(leftType) && !isPrimitiveOrNull(rightType)) {
                                if (isComparingOldAndNew(left, right, oldItemName, newItemName)) {
                                    if (isExpressionReturned(node, method)) {
                                        context.report(
                                            ISSUE,
                                            node,
                                            context.getLocation(node),
                                            "Using identity equality (`==`) to compare items in `areContentsTheSame` is a bug; did you mean structural equality (`.equals()`)?"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                    val receiver = node.receiver
                    if (receiver != null) {
                        val receiverType = receiver.getExpressionType()
                        if (receiverType != null && !isPrimitiveOrNull(receiverType) && !checkTypeOverridesEquals(receiverType)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Comparing `${receiverType.presentableText}` using `equals` but the class does not override `equals`"
                            )
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun unwrap(expression: UExpression): UExpression {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    private fun isComparingOldAndNew(left: UExpression, right: UExpression, oldName: String, newName: String): Boolean {
        val leftUnwrapped = unwrap(left)
        val rightUnwrapped = unwrap(right)
        if (leftUnwrapped is USimpleNameReferenceExpression && rightUnwrapped is USimpleNameReferenceExpression) {
            val leftId = leftUnwrapped.identifier
            val rightId = rightUnwrapped.identifier
            return (leftId == oldName && rightId == newName) || (leftId == newName && rightId == oldName)
        }
        return false
    }

    private fun isExpressionReturned(node: UExpression, method: UMethod): Boolean {
        var current: UElement? = node
        while (current != null && current != method) {
            val parent = current.uastParent
            if (parent is UReturnExpression) {
                return true
            }
            if (parent is UMethod) {
                return parent.body == current
            }
            if (parent is UBlockExpression) {
                if (parent.expressions.lastOrNull() != current) {
                    return false
                }
            }
            current = parent
        }
        return false
    }

    private fun isNullLiteral(expression: UExpression): Boolean {
        val unwrapped = unwrap(expression)
        return unwrapped is ULiteralExpression && unwrapped.value == null
    }

    private fun isPrimitiveOrNull(type: PsiType?): Boolean {
        if (type == null) return true
        if (type is PsiPrimitiveType) return true
        val canonicalText = type.canonicalText
        return canonicalText == "java.lang.Boolean" ||
                canonicalText == "java.lang.Byte" ||
                canonicalText == "java.lang.Character" ||
                canonicalText == "java.lang.Double" ||
                canonicalText == "java.lang.Float" ||
                canonicalText == "java.lang.Integer" ||
                canonicalText == "java.lang.Long" ||
                canonicalText == "java.lang.Short"
    }

    private fun checkTypeOverridesEquals(type: PsiType?): Boolean {
        if (type == null) return true
        if (type is PsiPrimitiveType) return true
        if (type is PsiArrayType) return false
        val psiClass = (type as? PsiClassType)?.resolve() ?: return true
        return overridesEquals(psiClass)
    }

    private fun overridesEquals(psiClass: PsiClass?): Boolean {
        if (psiClass == null) return true
        if (psiClass.isInterface) return true
        if (psiClass.isEnum) return true
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") return false

        val methods = psiClass.findMethodsByName("equals", true)
        for (method in methods) {
            val containingClass = method.containingClass ?: continue
            val qName = containingClass.qualifiedName
            if (qName != "java.lang.Object" && qName != "kotlin.Any") {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                    val paramType = parameters[0].type
                    val paramTypeName = paramType.canonicalText
                    if (paramTypeName == "java.lang.Object" || paramTypeName == "kotlin.Any") {
                        return true
                    }
                }
            }
        }
        return false
    }
}