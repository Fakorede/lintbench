package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), Detector.UastScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val areContentsTheSameMethod = declaration.methods.firstOrNull {
            it.name == "areContentsTheSame" && it.parameterList.parametersCount == 2
        } ?: return

        val parameters = areContentsTheSameMethod.parameterList.parameters
        val itemType = parameters[0].type

        areContentsTheSameMethod.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                if (operator == UastBinaryOperator.EQUALS || 
                    operator == UastBinaryOperator.NOT_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_EQUALS || 
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {

                    val leftType = node.leftOperand.getExpressionType()
                    val rightType = node.rightOperand.getExpressionType()

                    if (leftType != null && rightType != null) {
                        val isLeftMatch = context.evaluator.typeMatches(leftType, itemType.canonicalText)
                        val isRightMatch = context.evaluator.typeMatches(rightType, itemType.canonicalText)

                        if (isLeftMatch || isRightMatch) {
                            val isIdentity = operator == UastBinaryOperator.IDENTITY_EQUALS || 
                                             operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                            if (isIdentity) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Use of identity equality (`===` or `!==`) instead of structural equality (`==` or `!=`) in `areContentsTheSame`"
                                )
                            } else {
                                val typeToCheck = if (isLeftMatch) leftType else rightType
                                val psiClass = (typeToCheck as? PsiClassType)?.resolve()
                                if (psiClass != null && !overridesEquals(psiClass)) {
                                    context.report(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        "`${psiClass.name}` does not override `equals`, so structural equality (`==`) will run identity equality"
                                    )
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
                        if (receiverType != null && context.evaluator.typeMatches(receiverType, itemType.canonicalText)) {
                            val psiClass = (receiverType as? PsiClassType)?.resolve()
                            if (psiClass != null && !overridesEquals(psiClass)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "`${psiClass.name}` does not override `equals`, so calling `equals` will run identity equality"
                                )
                            }
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass is PsiTypeParameter) return true
        if (psiClass.isInterface) return true
        if (psiClass.isEnum) return true
        if (psiClass.superClass?.qualifiedName == "java.lang.Record") return true

        if (psiClass is KtLightClass) {
            val origin = psiClass.kotlinOrigin
            if (origin is KtClass && origin.isData()) {
                return true
            }
        }

        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") {
                break
            }
            for (method in current.findMethodsByName("equals", false)) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1 && parameters[0].type.canonicalText == "java.lang.Object") {
                    return true
                }
            }
            current = current.superClass
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}