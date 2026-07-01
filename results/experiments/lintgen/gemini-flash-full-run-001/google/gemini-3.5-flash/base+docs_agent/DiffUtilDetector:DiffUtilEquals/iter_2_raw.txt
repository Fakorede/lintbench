package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented
                incorrectly, such as using identity equals instead of equals, or calling equals on a class
                that has not implemented it, weird visual artifacts can occur.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val areContentsTheSameMethod = declaration.methods.firstOrNull { method ->
            method.name == "areContentsTheSame" && method.uastParameters.size == 2
        } ?: return

        val parameters = areContentsTheSameMethod.uastParameters
        val p1 = parameters[0]
        val p2 = parameters[1]

        val p1Type = p1.type
        val psiClass = (p1Type as? PsiClassType)?.resolve() ?: return

        if (psiClass is PsiTypeParameter) {
            return
        }

        val overrides = overridesEquals(psiClass)
        val p1Psi = p1.javaPsi
        val p2Psi = p2.javaPsi

        areContentsTheSameMethod.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val op = node.operator
                val isIdentity = op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS
                val isEquality = op == UastBinaryOperator.EQUALS || op == UastBinaryOperator.NOT_EQUALS

                if (isIdentity || (isEquality && !overrides)) {
                    val left = node.leftOperand
                    val right = node.rightOperand
                    if ((isReferenceToParameter(left, p1Psi) && isReferenceToParameter(right, p2Psi)) ||
                        (isReferenceToParameter(left, p2Psi) && isReferenceToParameter(right, p1Psi))) {
                        report(node, isIdentity)
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (overrides) return super.visitCallExpression(node)

                val methodName = node.methodName
                if (methodName == "equals") {
                    val receiver = node.receiver
                    val arguments = node.valueArguments
                    if (arguments.size == 1) {
                        val arg = arguments[0]
                        if ((isReferenceToParameter(receiver, p1Psi) && isReferenceToParameter(arg, p2Psi)) ||
                            (isReferenceToParameter(receiver, p2Psi) && isReferenceToParameter(arg, p1Psi))) {
                            report(node, false)
                        }
                    } else if (arguments.size == 2) {
                        val evaluator = context.evaluator
                        if (evaluator.isMemberInClass(node.resolve(), "java.util.Objects")) {
                            val arg1 = arguments[0]
                            val arg2 = arguments[1]
                            if ((isReferenceToParameter(arg1, p1Psi) && isReferenceToParameter(arg2, p2Psi)) ||
                                (isReferenceToParameter(arg1, p2Psi) && isReferenceToParameter(arg2, p1Psi))) {
                                report(node, false)
                            }
                        }
                    }
                } else if (methodName == "areEqual") {
                    val evaluator = context.evaluator
                    if (evaluator.isMemberInClass(node.resolve(), "kotlin.jvm.internal.Intrinsics")) {
                        val arguments = node.valueArguments
                        if (arguments.size == 2) {
                            val arg1 = arguments[0]
                            val arg2 = arguments[1]
                            if ((isReferenceToParameter(arg1, p1Psi) && isReferenceToParameter(arg2, p2Psi)) ||
                                (isReferenceToParameter(arg1, p2Psi) && isReferenceToParameter(arg2, p1Psi))) {
                                report(node, false)
                            }
                        }
                    }
                }
                return super.visitCallExpression(node)
            }

            private fun report(node: UElement, isIdentity: Boolean) {
                val typeName = psiClass.name ?: "the item class"
                val message = if (isIdentity) {
                    "Using identity equality (`===`) in `areContentsTheSame` is usually an error"
                } else {
                    "Comparing `$typeName` using generic `equals` (or `==`) but the class does not override `equals`"
                }
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        })
    }

    private fun UExpression.skipParenthesesAndCasts(): UExpression {
        var current = this
        while (true) {
            if (current is UParenthesizedExpression) {
                current = current.expression
            } else if (current is UBinaryExpressionWithTypeRhs) {
                current = current.operand
            } else if (current is UTypeCastExpression) {
                current = current.expression
            } else {
                break
            }
        }
        return current
    }

    private fun isReferenceToParameter(expression: UExpression?, parameter: PsiElement?): Boolean {
        if (expression == null || parameter == null) return false
        val unwrapped = expression.skipParenthesesAndCasts()
        val resolved = (unwrapped as? UReferenceExpression)?.resolve() ?: return false
        if (resolved == parameter) return true
        val resolvedSource = resolved.navigationElement
        val parameterSource = parameter.navigationElement
        return resolvedSource == parameterSource
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
            return false
        }
        if (psiClass.isInterface) {
            return true
        }
        if (psiClass.isEnum) {
            return true
        }

        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") {
                break
            }
            for (method in current.findMethodsByName("equals", false)) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                    val paramType = parameters[0].type
                    val canonicalText = paramType.canonicalText
                    if (canonicalText == "java.lang.Object" || canonicalText == "any" || canonicalText == "kotlin.Any") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }
}