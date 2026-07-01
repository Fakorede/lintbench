package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiArrayType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return DiffUtilHandler(context)
    }

    private class DiffUtilHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != "areContentsTheSame") return
            val containingClass = node.containingClass ?: return

            val evaluator = context.evaluator
            val isItemCallback = evaluator.inheritsFrom(containingClass, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
                    evaluator.inheritsFrom(containingClass, "android.support.v7.util.DiffUtil.ItemCallback", false)

            if (!isItemCallback) return

            val parameters = node.uastParameters
            if (parameters.size != 2) return
            val p1 = parameters[0].javaPsi
            val p2 = parameters[1].javaPsi

            node.accept(object : AbstractUastVisitor() {
                override fun visitBinaryExpression(expression: UBinaryExpression): Boolean {
                    val operator = expression.operator
                    val left = skipParenthesesAndCasts(expression.leftOperand)
                    val right = skipParenthesesAndCasts(expression.rightOperand)

                    if (isParamRef(left, p1, p2) && isParamRef(right, p1, p2)) {
                        val isKotlin = isKotlin(expression.sourcePsi)
                        if (isKotlin) {
                            if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                                reportIdentityComparison(expression)
                            } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                                checkMissingEquals(expression, left, right)
                            }
                        } else {
                            if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                                reportIdentityComparison(expression)
                            }
                        }
                    }
                    return super.visitBinaryExpression(expression)
                }

                override fun visitCallExpression(expression: UCallExpression): Boolean {
                    if (expression.methodName == "equals" && expression.valueArgumentCount == 1) {
                        val receiver = expression.receiver?.let { skipParenthesesAndCasts(it) }
                        val argument = expression.valueArguments[0].let { skipParenthesesAndCasts(it) }
                        if (receiver != null && isParamRef(receiver, p1, p2) && isParamRef(argument, p1, p2)) {
                            checkMissingEquals(expression, receiver, argument)
                        }
                    }
                    return super.visitCallExpression(expression)
                }

                private fun checkMissingEquals(expression: UExpression, left: UExpression, right: UExpression) {
                    val type = left.getExpressionType() ?: right.getExpressionType()
                    if (type is PsiArrayType) {
                        reportMissingEqualsForArray(expression)
                        return
                    }
                    val psiClass = (type as? PsiClassType)?.resolve()
                    if (psiClass != null && !overridesEquals(psiClass)) {
                        reportMissingEquals(expression, psiClass)
                    }
                }

                private fun isParamRef(expression: UExpression, p1: PsiParameter, p2: PsiParameter): Boolean {
                    val resolved = (expression as? USimpleNameReferenceExpression)?.resolve() ?: return false
                    return resolved == p1 || resolved == p2
                }

                private fun skipParenthesesAndCasts(expression: UExpression): UExpression {
                    var current = expression
                    while (true) {
                        if (current is UParenthesizedExpression) {
                            current = current.expression
                        } else if (current is UBinaryExpressionWithType) {
                            current = current.operand
                        } else {
                            break
                        }
                    }
                    return current
                }

                private fun overridesEquals(psiClass: PsiClass?): Boolean {
                    if (psiClass == null) return true
                    if (psiClass.isInterface) return true
                    if (psiClass.isEnum) return true

                    val qName = psiClass.qualifiedName
                    if (qName == "java.lang.Object" || qName == "kotlin.Any") return false
                    if (qName != null && (
                        qName.startsWith("java.lang.") ||
                        qName.startsWith("java.util.") ||
                        qName.startsWith("kotlin.") ||
                        qName.startsWith("android.net.Uri")
                    )) {
                        return true
                    }

                    var current: PsiClass? = psiClass
                    while (current != null) {
                        val currentQName = current.qualifiedName
                        if (currentQName == "java.lang.Object" || currentQName == "kotlin.Any") {
                            break
                        }
                        val methods = current.findMethodsByName("equals", false)
                        for (method in methods) {
                            val parameterList = method.parameterList
                            if (parameterList.parametersCount == 1) {
                                val paramType = parameterList.parameters[0].type
                                if (paramType.canonicalText == "java.lang.Object") {
                                    return true
                                }
                            }
                        }
                        current = current.superClass
                    }
                    return false
                }

                private fun reportIdentityComparison(expression: UExpression) {
                    context.report(
                        ISSUE,
                        expression,
                        context.getLocation(expression),
                        "Suspicious equality check: `areContentsTheSame` should compare the contents of the fields, not the object identities"
                    )
                }

                private fun reportMissingEquals(expression: UExpression, psiClass: PsiClass) {
                    context.report(
                        ISSUE,
                        expression,
                        context.getLocation(expression),
                        "`areContentsTheSame` is using `equals` but `${psiClass.name}` does not override `equals`"
                    )
                }

                private fun reportMissingEqualsForArray(expression: UExpression) {
                    context.report(
                        ISSUE,
                        expression,
                        context.getLocation(expression),
                        "`areContentsTheSame` is comparing arrays using `equals` or `==` which compares identity; use `Arrays.equals` or `.contentEquals` instead"
                    )
                }
            })
        }
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}