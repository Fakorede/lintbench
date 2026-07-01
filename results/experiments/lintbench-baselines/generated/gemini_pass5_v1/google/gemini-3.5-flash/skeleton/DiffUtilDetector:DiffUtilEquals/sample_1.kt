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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastBinaryOperator

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
                implemented incorrectly, such as using identity equals instead of equals, \
                or calling equals on a class that has not implemented it, weird visual \
                artifacts can occur.
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
            "androidx.recyclerview.widget.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == "areContentsTheSame") {
                val uMethod = context.uastContext.getMethod(method)
                uMethod.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
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
    }

    fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator
        val left = node.leftOperand
        val right = node.rightOperand
        val leftType = left.getExpressionType() ?: return
        val rightType = right.getExpressionType() ?: return

        if (leftType is PsiPrimitiveType || rightType is PsiPrimitiveType) {
            return
        }

        val isKotlin = context.file.name.endsWith(".kt")

        if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use of identity equality (`===`) in `areContentsTheSame` is suspicious"
            )
        } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
            if (isKotlin) {
                val psiClass = getPsiClass(leftType)
                if (psiClass != null && !overridesEquals(psiClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Comparing objects of type `${psiClass.name}` using `==` is suspicious because it does not override `equals`"
                    )
                } else if (leftType is PsiArrayType) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Comparing arrays using `==` is suspicious (compares identity, not contents)"
                    )
                }
            } else {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use of identity equality (`==`) in `areContentsTheSame` is suspicious"
                )
            }
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName == "equals" && node.valueArgumentCount == 1) {
            val receiver = node.receiver ?: return
            val receiverType = receiver.getExpressionType() ?: return
            if (receiverType is PsiPrimitiveType) return

            if (receiverType is PsiArrayType) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Comparing arrays using `equals` is suspicious (compares identity, not contents)"
                )
                return
            }

            val psiClass = getPsiClass(receiverType)
            if (psiClass != null && !overridesEquals(psiClass)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling `equals` on `${psiClass.name}` is suspicious because it does not override `equals`"
                )
            }
        } else if (methodName == "equals" && node.valueArgumentCount == 2 && node.receiver == null) {
            val firstArg = node.valueArguments.firstOrNull() ?: return
            val firstType = firstArg.getExpressionType() ?: return
            if (firstType is PsiPrimitiveType) return
            if (firstType is PsiArrayType) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Comparing arrays using `Objects.equals` is suspicious (compares identity, not contents)"
                )
                return
            }
            val psiClass = getPsiClass(firstType)
            if (psiClass != null && !overridesEquals(psiClass)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Comparing objects of type `${psiClass.name}` using `Objects.equals` is suspicious because the class does not override `equals`"
                )
            }
        }
    }

    private fun getPsiClass(type: PsiType): PsiClass? {
        return (type as? PsiClassType)?.resolve()
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) return true
        if (psiClass.isEnum) return true
        val className = psiClass.qualifiedName
        if (className == "java.lang.Object" || className == "kotlin.Any") return false

        val sourcePsi = (psiClass as? UClass)?.sourcePsi ?: psiClass.navigationElement
        if (sourcePsi != null && sourcePsi.javaClass.name.endsWith("KtClass")) {
            try {
                val isDataMethod = sourcePsi.javaClass.getMethod("isData")
                if (isDataMethod.invoke(sourcePsi) == true) {
                    return true
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        if (psiClass.javaClass.name.contains("LightClass") || psiClass.javaClass.name.contains("Light")) {
            try {
                val getKotlinOrigin = psiClass.javaClass.getMethod("getKotlinOrigin")
                val origin = getKotlinOrigin.invoke(psiClass)
                if (origin != null && origin.javaClass.name.endsWith("KtClass")) {
                    val isDataMethod = origin.javaClass.getMethod("isData")
                    if (isDataMethod.invoke(origin) == true) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        val methods = psiClass.findMethodsByName("equals", true)
        for (method in methods) {
            val containingClass = method.containingClass ?: continue
            val containingClassName = containingClass.qualifiedName
            if (containingClassName != "java.lang.Object" && containingClassName != "kotlin.Any") {
                if (method.parameterList.parametersCount == 1) {
                    val paramType = method.parameterList.parameters[0].type
                    val paramTypeFq = paramType.canonicalText
                    if (paramTypeFq == "java.lang.Object" || paramTypeFq == "kotlin.Any") {
                        return true
                    }
                }
            }
        }
        return false
    }
}