package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isJava
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
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

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val isItemCallback = context.evaluator.inheritsFrom(
            declaration, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false
        )
        if (!isItemCallback) return

        val method = declaration.methods.firstOrNull { it.name == "areContentsTheSame" } ?: return
        val uMethod = context.uastContext.getMethod(method) ?: return
        checkMethod(context, uMethod)
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val parameters = method.uastParameters
        if (parameters.size != 2) return

        val p1 = parameters[0]
        val p2 = parameters[1]

        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                val isJavaFile = isJava(node.sourcePsi)

                val isReferenceComparison = if (isJavaFile) {
                    operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS
                } else {
                    operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                }

                if (isReferenceComparison) {
                    if (isComparingParameters(node.leftOperand, node.rightOperand, p1, p2)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Comparison using reference equality instead of contents equality"
                        )
                    }
                } else if (!isJavaFile && (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS)) {
                    if (isComparingParameters(node.leftOperand, node.rightOperand, p1, p2)) {
                        val type = p1.type as? PsiClassType
                        val psiClass = type?.resolve()
                        if (psiClass != null && !overridesEquals(psiClass)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "`${psiClass.name}` does not override `equals()`; comparing it with `==` will use reference equality"
                            )
                        }
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                    val receiver = node.receiver
                    val argument = node.valueArguments[0]
                    if (receiver != null && isComparingElements(receiver, argument, p1, p2)) {
                        val type = p1.type as? PsiClassType
                        val psiClass = type?.resolve()
                        if (psiClass != null && !overridesEquals(psiClass)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "`${psiClass.name}` does not override `equals()`; calling `equals()` will use reference equality"
                            )
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun isComparingParameters(
        left: UExpression,
        right: UExpression,
        p1: UParameter,
        p2: UParameter
    ): Boolean {
        val leftTarget = (left as? UReferenceExpression)?.resolve()
        val rightTarget = (right as? UReferenceExpression)?.resolve()
        return (leftTarget == p1.psi && rightTarget == p2.psi) ||
               (leftTarget == p2.psi && rightTarget == p1.psi)
    }

    private fun isComparingElements(
        receiver: UExpression,
        argument: UExpression,
        p1: UParameter,
        p2: UParameter
    ): Boolean {
        val recTarget = (receiver as? UReferenceExpression)?.resolve()
        val argTarget = (argument as? UReferenceExpression)?.resolve()
        return (recTarget == p1.psi && argTarget == p2.psi) ||
               (recTarget == p2.psi && argTarget == p1.psi)
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) return true
        if (psiClass.isEnum) return true

        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null) {
            if (qualifiedName.startsWith("java.lang.") ||
                qualifiedName.startsWith("java.util.") ||
                qualifiedName.startsWith("kotlin.") ||
                qualifiedName.startsWith("android.net.Uri")) {
                if (qualifiedName == "java.lang.Object") return false
                return true
            }
        }

        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object") {
                break
            }

            if (current is KtLightClass) {
                val origin = current.kotlinOrigin
                if (origin is KtClass && origin.isData()) {
                    return true
                }
            }

            val methods = current.findMethodsByName("equals", false)
            for (m in methods) {
                val params = m.parameterList.parameters
                if (params.size == 1) {
                    val paramType = params[0].type.canonicalText
                    if (paramType == "java.lang.Object" || paramType == "any" || paramType == "kotlin.Any") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }
}