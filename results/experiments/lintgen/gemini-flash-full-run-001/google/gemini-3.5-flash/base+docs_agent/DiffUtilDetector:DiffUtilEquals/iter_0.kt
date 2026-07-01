package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented \
                incorrectly, such as using identity equals instead of equals, or calling equals on a class \
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
        val p1 = parameters[0].javaPsi
        val p2 = parameters[1].javaPsi

        val p1Type = p1.type
        val psiClass = (p1Type as? PsiClassType)?.resolve() ?: return

        if (psiClass is com.intellij.psi.PsiTypeParameter) {
            return
        }

        if (!overridesEquals(psiClass)) {
            areContentsTheSameMethod.accept(object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    val op = node.operator
                    if (op == UastBinaryOperator.EQUALS || op == UastBinaryOperator.NOT_EQUALS ||
                        op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                        val left = node.leftOperand
                        val right = node.rightOperand
                        if ((isReferenceToParameter(left, p1) && isReferenceToParameter(right, p2)) ||
                            (isReferenceToParameter(left, p2) && isReferenceToParameter(right, p1))) {
                            report(node)
                        }
                    }
                    return super.visitBinaryExpression(node)
                }

                override fun visitMethodCallExpression(node: UMethodCallExpression): Boolean {
                    val methodName = node.methodName
                    if (methodName == "equals") {
                        val receiver = node.receiver
                        val arguments = node.valueArguments
                        if (arguments.size == 1) {
                            val arg = arguments[0]
                            if ((isReferenceToParameter(receiver, p1) && isReferenceToParameter(arg, p2)) ||
                                (isReferenceToParameter(receiver, p2) && isReferenceToParameter(arg, p1))) {
                                report(node)
                            }
                        }
                    } else if (methodName == "equals" && node.receiver == null) {
                        val evaluator = context.evaluator
                        if (evaluator.isMemberInClass(node.resolve(), "java.util.Objects")) {
                            val arguments = node.valueArguments
                            if (arguments.size == 2) {
                                val arg1 = arguments[0]
                                val arg2 = arguments[1]
                                if ((isReferenceToParameter(arg1, p1) && isReferenceToParameter(arg2, p2)) ||
                                    (isReferenceToParameter(arg1, p2) && isReferenceToParameter(arg2, p1))) {
                                    report(node)
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
                                if ((isReferenceToParameter(arg1, p1) && isReferenceToParameter(arg2, p2)) ||
                                    (isReferenceToParameter(arg1, p2) && isReferenceToParameter(arg2, p1))) {
                                    report(node)
                                }
                            }
                        }
                    }
                    return super.visitMethodCallExpression(node)
                }

                private fun report(node: UElement) {
                    val typeName = psiClass.name ?: "the item class"
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Comparing `$typeName` using generic `equals` (or `==`) but the class does not override `equals`"
                    )
                }
            })
        }
    }

    private fun isReferenceToParameter(expression: UExpression?, parameter: PsiParameter): Boolean {
        if (expression == null) return false
        val resolved = (expression as? UReferenceExpression)?.resolve()
        return resolved == parameter
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
                    if (paramType.canonicalText == "java.lang.Object" || paramType.canonicalText == "any") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }
}