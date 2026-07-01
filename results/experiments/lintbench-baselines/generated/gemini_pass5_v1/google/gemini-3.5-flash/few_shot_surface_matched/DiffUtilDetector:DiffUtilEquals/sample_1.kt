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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val areContentsTheSameMethods = declaration.methods.filter { it.name == "areContentsTheSame" }
        for (method in areContentsTheSameMethods) {
            method.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    this@DiffUtilDetector.visitBinaryExpression(context, node)
                    return super.visitBinaryExpression(node)
                }

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    this@DiffUtilDetector.visitCallExpression(context, node)
                    return super.visitCallExpression(node)
                }
            })
        }
    }

    fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator
        val isEquals = operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS
        val isIdentity = operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS

        if (!isEquals && !isIdentity) return

        val leftType = node.leftOperand.getExpressionType() ?: return
        val rightType = node.rightOperand.getExpressionType() ?: return

        if (isPrimitiveOrBoxed(leftType.canonicalText) || isPrimitiveOrBoxed(rightType.canonicalText)) {
            return
        }

        val isKotlin = com.android.tools.lint.detector.api.isKotlin(node.sourcePsi)

        if (isKotlin) {
            if (isIdentity) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: use of `===` (identity equality) in `areContentsTheSame` is usually an error"
                )
            } else if (isEquals) {
                val psiClass = context.evaluator.getTypeClass(leftType)
                if (psiClass != null && !overridesEquals(psiClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious equality check: `equals()` is not overridden in `${psiClass.qualifiedName}`"
                    )
                }
            }
        } else {
            if (isEquals) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: use of `==` (identity equality) in `areContentsTheSame` is usually an error; use `equals()` instead"
                )
            }
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName == "equals") {
            val receiver = node.receiver
            val receiverType = receiver?.getExpressionType() ?: node.valueArguments.firstOrNull()?.getExpressionType() ?: return
            if (isPrimitiveOrBoxed(receiverType.canonicalText)) return

            val psiClass = context.evaluator.getTypeClass(receiverType) ?: return
            if (!overridesEquals(psiClass)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: `equals()` is not overridden in `${psiClass.qualifiedName}`"
                )
            }
        }
    }

    private fun isPrimitiveOrBoxed(typeName: String): Boolean {
        return when (typeName) {
            "int", "double", "float", "long", "short", "byte", "char", "boolean",
            "java.lang.Integer", "java.lang.Double", "java.lang.Float",
            "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Character", "java.lang.Boolean" -> true
            else -> false
        }
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) return true
        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") {
                break
            }
            if (qName == "java.lang.String" ||
                qName?.startsWith("java.lang.") == true ||
                qName?.startsWith("java.util.") == true ||
                qName?.startsWith("kotlin.") == true) {
                return true
            }

            for (method in current.methods) {
                if (method.name == "equals" && method.parameterList.parametersCount == 1) {
                    val paramType = method.parameterList.parameters[0].type.canonicalText
                    if (paramType == "java.lang.Object" || paramType == "any") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }
}