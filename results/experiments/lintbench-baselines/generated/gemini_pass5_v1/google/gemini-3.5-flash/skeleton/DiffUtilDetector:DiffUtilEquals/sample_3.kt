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
import org.jetbrains.uast.UElement
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
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val areContentsTheSameMethod = declaration.methods.find { it.name == "areContentsTheSame" } ?: return
        traverse(context, areContentsTheSameMethod)
    }

    private fun traverse(context: JavaContext, node: UElement) {
        if (node is UBinaryExpression) {
            visitBinaryExpression(context, node)
        } else if (node is UCallExpression) {
            visitCallExpression(context, node)
        }
        for (child in node.uastChildren) {
            traverse(context, child)
        }
    }

    private fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val opText = node.operator.text
        val isKotlin = context.file.name.endsWith(".kt")
        
        val isReferenceComparison = if (isKotlin) {
            opText == "===" || opText == "!=="
        } else {
            opText == "==" || opText == "!="
        }
        
        if (isReferenceComparison) {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()
            val isLeftPrimitive = leftType is PsiPrimitiveType
            val isRightPrimitive = rightType is PsiPrimitiveType
            
            if (!isLeftPrimitive && !isRightPrimitive && !isNullLiteral(node.leftOperand) && !isNullLiteral(node.rightOperand)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use equals() instead of reference equality"
                )
            }
        } else if (isKotlin && (opText == "==" || opText == "!=")) {
            if (!isNullLiteral(node.leftOperand) && !isNullLiteral(node.rightOperand)) {
                val type = node.leftOperand.getExpressionType()
                if (type != null) {
                    checkTypeImplementsEquals(context, node, type)
                }
            }
        }
    }

    private fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: node.methodIdentifier?.name
        if (methodName == "equals" && node.valueArgumentCount == 1) {
            val receiver = node.receiver
            if (receiver != null) {
                val receiverType = receiver.getExpressionType()
                if (receiverType != null) {
                    checkTypeImplementsEquals(context, node, receiverType)
                }
            }
        }
    }

    private fun checkTypeImplementsEquals(context: JavaContext, node: UElement, type: PsiType) {
        if (type is PsiPrimitiveType) return
        
        if (type is PsiArrayType) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Comparing arrays using equals() is not reliable; use contentDeepEquals() or contentEquals() instead"
            )
            return
        }
        
        val psiClass = (type as? PsiClassType)?.resolve() ?: return
        if (psiClass is PsiTypeParameter) return

        if (!implementsEquals(psiClass)) {
            val className = psiClass.name ?: "this class"
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "AreContentsTheSame should not use equals() on $className because it does not implement equals()"
            )
        }
    }

    private fun implementsEquals(psiClass: PsiClass): Boolean {
        var current: PsiClass? = psiClass
        while (current != null) {
            val qualifiedName = current.qualifiedName
            if (qualifiedName == "java.lang.Object") {
                return false
            }
            if (current.isInterface || current.isEnum) {
                return true
            }
            for (method in current.methods) {
                if (method.name == "equals" && method.parameterList.parametersCount == 1) {
                    val parameter = method.parameterList.parameters[0]
                    val type = parameter.type
                    if (type.canonicalText == "java.lang.Object") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }

    private fun isNullLiteral(expression: UExpression): Boolean {
        return expression is ULiteralExpression && expression.value == null
    }
}