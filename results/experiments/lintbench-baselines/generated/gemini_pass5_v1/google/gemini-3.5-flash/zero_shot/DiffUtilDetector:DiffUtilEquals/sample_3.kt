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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), Detector.UastScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods.filter { it.name == "areContentsTheSame" }
        for (method in methods) {
            method.accept(DiffUtilVisitor(context))
        }
    }

    private class DiffUtilVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            val operator = node.operator
            if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using identity equality (`===`) in `areContentsTheSame` is suspicious; did you mean content equality (`==`)?"
                )
            } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                checkTypeOverridesEquals(node.leftOperand, node)
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                val resolved = node.resolve()
                if (resolved != null) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == "java.lang.Object") {
                        val receiverType = node.receiverType
                        if (receiverType is PsiClassType) {
                            val psiClass = receiverType.resolve()
                            if (psiClass != null && !psiClass.isInterface && !overridesEquals(psiClass)) {
                                reportMissingEquals(node, psiClass)
                            }
                        }
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun checkTypeOverridesEquals(expression: UExpression, node: UElement) {
            val type = expression.getExpressionType()
            if (type is PsiClassType) {
                val psiClass = type.resolve()
                if (psiClass != null && !psiClass.isInterface && !overridesEquals(psiClass)) {
                    reportMissingEquals(node, psiClass)
                }
            }
        }

        private fun reportMissingEquals(node: UElement, psiClass: PsiClass) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`areContentsTheSame` checks equality of `${psiClass.name}`, which does not override `equals`"
            )
        }

        private fun overridesEquals(psiClass: PsiClass): Boolean {
            val qualifiedName = psiClass.qualifiedName
            if (qualifiedName == "java.lang.Object") {
                return false
            }
            if (qualifiedName != null) {
                if (qualifiedName == "java.lang.String" ||
                    qualifiedName.startsWith("java.lang.Number") ||
                    qualifiedName == "java.lang.Integer" ||
                    qualifiedName == "java.lang.Long" ||
                    qualifiedName == "java.lang.Double" ||
                    qualifiedName == "java.lang.Float" ||
                    qualifiedName == "java.lang.Boolean" ||
                    qualifiedName == "java.lang.Character" ||
                    qualifiedName == "java.util.UUID"
                ) {
                    return true
                }
            }
            if (psiClass.isEnum || psiClass.isInterface) {
                return true
            }
            val uClass = psiClass.toUElementOfType<UClass>()
            if (uClass != null) {
                val sourcePsi = uClass.sourcePsi
                if (sourcePsi is KtClass && sourcePsi.isData()) {
                    return true
                }
            }
            for (method in psiClass.findMethodsByName("equals", false)) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                    val paramType = parameters[0].type
                    if (paramType.canonicalText == "java.lang.Object") {
                        return true
                    }
                }
            }
            val superClass = psiClass.superClass
            if (superClass != null && superClass.qualifiedName != "java.lang.Object") {
                return overridesEquals(superClass)
            }
            return false
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
}