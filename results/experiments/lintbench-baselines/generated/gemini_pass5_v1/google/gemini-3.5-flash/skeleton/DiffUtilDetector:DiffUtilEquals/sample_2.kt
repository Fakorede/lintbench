package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        val areContentsTheSameMethod = methods.find { it.name == "areContentsTheSame" } ?: return

        areContentsTheSameMethod.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                visitBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                visitCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator.text
        val left = node.leftOperand
        val right = node.rightOperand

        if (left.isNullLiteral() || right.isNullLiteral()) {
            return
        }

        val isKotlin = node.sourcePsi?.language?.id?.equals("kotlin", ignoreCase = true) == true

        if (isKotlin) {
            if (operator == "===" || operator == "!==") {
                val leftType = left.getExpressionType()
                val rightType = right.getExpressionType()
                if (isSuspiciousType(leftType) || isSuspiciousType(rightType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using identity equality `===` instead of structural equality `==` inside `areContentsTheSame`"
                    )
                }
            } else if (operator == "==" || operator == "!=") {
                val leftType = left.getExpressionType()
                if (isSuspiciousType(leftType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Comparing items using `==` but the type `${leftType?.presentableText}` does not override `equals`"
                    )
                }
            }
        } else {
            if (operator == "==" || operator == "!=") {
                val leftType = left.getExpressionType()
                val rightType = right.getExpressionType()
                if (isSuspiciousType(leftType) || isSuspiciousType(rightType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using identity equality `==` instead of `equals()` inside `areContentsTheSame`"
                    )
                }
            }
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: node.methodIdentifier?.name
        if (methodName == "equals" && node.valueArgumentCount == 1) {
            val receiverType = node.receiverType
            if (isSuspiciousType(receiverType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling `equals` but the type `${receiverType?.presentableText}` does not override `equals`"
                )
            }
        }
    }

    private fun isSuspiciousType(type: PsiType?): Boolean {
        if (type == null || type is PsiPrimitiveType) {
            return false
        }
        if (type is PsiArrayType) {
            return true
        }
        val psiClass = (type as? PsiClassType)?.resolve() ?: return false
        if (psiClass is PsiTypeParameter) {
            return false
        }
        return !overridesEquals(psiClass)
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isEnum || psiClass.isInterface) {
            return true
        }
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "java.lang.Object") {
            return false
        }
        if (qualifiedName != null) {
            if (qualifiedName == "java.lang.String" ||
                qualifiedName.startsWith("java.lang.Integer") ||
                qualifiedName.startsWith("java.lang.Long") ||
                qualifiedName.startsWith("java.lang.Double") ||
                qualifiedName.startsWith("java.lang.Float") ||
                qualifiedName.startsWith("java.lang.Boolean") ||
                qualifiedName.startsWith("java.lang.Byte") ||
                qualifiedName.startsWith("java.lang.Character") ||
                qualifiedName.startsWith("java.lang.Short")
            ) {
                return true
            }
        }

        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object") {
                break
            }
            for (method in current.findMethodsByName("equals", false)) {
                val params = method.parameterList.parameters
                if (params.size == 1 && params[0].type.canonicalText == "java.lang.Object") {
                    return true
                }
            }
            current = current.superClass
        }
        return false
    }

    private fun UExpression?.isNullLiteral(): Boolean {
        return this is ULiteralExpression && this.value == null
    }
}