package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getExpressionType
import org.jetbrains.uast.visitor.AbstractUVisitor

private const val METHOD_NAME = "areContentsTheSame"
private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
private const val SUPPORT_DIFF_UTIL_CALLBACK = "android.support.v7.util.DiffUtil.Callback"
private const val SUPPORT_DIFF_UTIL_ITEM_CALLBACK = "android.support.v7.util.DiffUtil.ItemCallback"

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DiffUtilHandler(context)

    private class DiffUtilHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != METHOD_NAME) return
            if (node.uastParameters.size != 2) return
            if (node.returnType?.canonicalText != "boolean") return

            val containingClass = node.containingClass ?: return
            if (!isDiffUtilCallback(containingClass)) return

            node.uastBody?.accept(SuspiciousEqualityVisitor(context))
        }
    }

    private class SuspiciousEqualityVisitor(
        private val context: JavaContext
    ) : AbstractUVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    report(
                        node,
                        "Using identity equality (=== or !==) in areContentsTheSame; prefer structural equality"
                    )
                }
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    if (context.isKotlin) {
                        checkKotlinEquals(node)
                    } else {
                        checkJavaEquals(node)
                    }
                }
                else -> Unit
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                val receiver = node.receiver
                val receiverType = receiver?.getExpressionType()
                if (receiverType != null && !receiverType.hasMeaningfulEquals()) {
                    report(
                        node,
                        "Calling equals() on `${receiverType.presentableText}`, which does not override equals; " +
                                "this behaves like identity equality"
                    )
                }
            }
            return super.visitCallExpression(node)
        }

        private fun checkJavaEquals(node: UBinaryExpression) {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()
            if (leftType != null && rightType != null &&
                !leftType.isPrimitiveOrBoxed() && !rightType.isPrimitiveOrBoxed()
            ) {
                report(
                    node,
                    "Using == or != on objects in areContentsTheSame checks identity, not equality; use equals() instead"
                )
            }
        }

        private fun checkKotlinEquals(node: UBinaryExpression) {
            val type = node.leftOperand.getExpressionType()
                ?: node.rightOperand.getExpressionType()
            if (type != null && !type.isPrimitiveOrBoxed() && !type.hasMeaningfulEquals()) {
                report(
                    node,
                    "== calls equals(), but `${type.presentableText}` does not override equals; " +
                            "this behaves like identity equality"
                )
            }
        }

        private fun report(node: UElement, message: String) {
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is
                implemented incorrectly, such as using identity equals instead of equals, or
                calling equals on a class that has not implemented it, weird visual artifacts
                can occur.
            """.trimIndent(),
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

private fun isDiffUtilCallback(containingClass: PsiClass): Boolean {
    return InheritanceUtil.isInheritor(containingClass, DIFF_UTIL_CALLBACK) ||
            InheritanceUtil.isInheritor(containingClass, DIFF_UTIL_ITEM_CALLBACK) ||
            InheritanceUtil.isInheritor(containingClass, SUPPORT_DIFF_UTIL_CALLBACK) ||
            InheritanceUtil.isInheritor(containingClass, SUPPORT_DIFF_UTIL_ITEM_CALLBACK)
}

private val JavaContext.isKotlin: Boolean
    get() = file.name.endsWith(".kt") || file.name.endsWith(".kts")

private fun PsiType.isPrimitiveOrBoxed(): Boolean {
    if (this is PsiPrimitiveType) return true
    val unboxed = PsiTypesUtil.unboxIfPossible(this)
    return unboxed is PsiPrimitiveType
}

private fun PsiType.hasMeaningfulEquals(): Boolean {
    if (isPrimitiveOrBoxed()) return true
    if (this is com.intellij.psi.PsiArrayType) return true
    if (this !is PsiClassType) return true

    val psiClass = resolve() ?: return true
    if (psiClass.isEnum || psiClass.isInterface || psiClass is PsiTypeParameter) return true
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true
    if (psiClass.qualifiedName == "java.lang.String") return true
    if (psiClass.qualifiedName == "java.lang.Object" || psiClass.qualifiedName == "kotlin.Any") return true

    val equalsMethods = psiClass.findMethodsByName("equals", true)
    return equalsMethods.any { method ->
        val params = method.parameterList.parameters
        params.size == 1 && params[0].type.canonicalText == "java.lang.Object"
    }
}