package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.tryResolve

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
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return
                val containingClass = node.containingClass ?: return
                val evaluator = context.evaluator
                val isDiffUtilCallback = evaluator.inheritsFrom(containingClass, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
                        evaluator.inheritsFrom(containingClass, "android.support.v7.util.DiffUtil.ItemCallback", false)
                if (!isDiffUtilCallback) return

                val parameters = node.uastParameters
                if (parameters.size != 2) return
                val psiParam1 = parameters[0].javaPsi
                val psiParam2 = parameters[1].javaPsi
                val itemType = parameters[0].type

                node.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(expression: UBinaryExpression): Boolean {
                        val operator = expression.operator
                        val isIdentity = operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                        val isEquality = operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS

                        if (!isIdentity && !isEquality) return super.visitBinaryExpression(expression)

                        val left = expression.leftOperand
                        val right = expression.rightOperand
                        val resolvedLeft = left.tryResolve()
                        val resolvedRight = right.tryResolve()

                        val comparesParams = (resolvedLeft == psiParam1 && resolvedRight == psiParam2) ||
                                             (resolvedLeft == psiParam2 && resolvedRight == psiParam1)

                        if (comparesParams) {
                            if (isIdentity) {
                                context.report(
                                    ISSUE,
                                    expression,
                                    context.getLocation(expression),
                                    "Suspicious equality check: `areContentsTheSame` should compare contents, not identity"
                                )
                            } else if (isEquality) {
                                checkTypeEquality(expression, itemType)
                            }
                        }
                        return super.visitBinaryExpression(expression)
                    }

                    override fun visitCallExpression(expression: UCallExpression): Boolean {
                        if (expression.methodName == "equals" && expression.valueArgumentCount == 1) {
                            val receiver = expression.receiver
                            val argument = expression.valueArguments[0]
                            val resolvedReceiver = receiver?.tryResolve()
                            val resolvedArgument = argument.tryResolve()

                            val comparesParams = (resolvedReceiver == psiParam1 && resolvedArgument == psiParam2) ||
                                                 (resolvedReceiver == psiParam2 && resolvedArgument == psiParam1)

                            if (comparesParams) {
                                checkTypeEquality(expression, itemType)
                            }
                        }
                        return super.visitCallExpression(expression)
                    }

                    private fun checkTypeEquality(expression: UElement, type: PsiType) {
                        if (type is PsiArrayType) {
                            context.report(
                                ISSUE,
                                expression,
                                context.getLocation(expression),
                                "Suspicious equality check: comparing arrays using `equals` or `==` is not supported (use `Arrays.equals` instead)"
                            )
                        } else {
                            val psiClass = (type as? PsiClassType)?.resolve()
                            if (psiClass != null && !overridesEquals(psiClass)) {
                                context.report(
                                    ISSUE,
                                    expression,
                                    context.getLocation(expression),
                                    "Suspicious equality check: `${psiClass.name}` does not override `equals`"
                                )
                            }
                        }
                    }
                })
            }
        }
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass is PsiTypeParameter) return true
        if (psiClass.isInterface) return true
        if (psiClass.isEnum) return true

        val methods = psiClass.findMethodsByName("equals", true)
        for (method in methods) {
            val containingClass = method.containingClass ?: continue
            val qualifiedName = containingClass.qualifiedName
            if (qualifiedName != "java.lang.Object" && qualifiedName != "kotlin.Any") {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                    val paramType = parameters[0].type.canonicalText
                    if (paramType.contains("Object") || paramType.contains("Any")) {
                        return true
                    }
                }
            }
        }
        return false
    }
}