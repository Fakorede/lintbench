package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val DIFF_UTIL_CALLBACKS = listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil.ItemCallback` to decide whether two items have the same content. Using identity equality (`==` or `===`) or calling `equals()` on a type that does not override `Object.equals()` can cause incorrect diffs and visual glitches. Use a proper structural equality check for the item type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = DIFF_UTIL_CALLBACKS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // The actual checks are performed on binary and call expressions once we have
        // verified that they appear inside areContentsTheSame().
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!insideAreContentsTheSame(context, node)) return
        if (isIdentityComparison(node)) {
            report(
                context,
                node,
                "Use `equals()` instead of identity equality in `areContentsTheSame`.",
            )
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!insideAreContentsTheSame(context, node)) return
        if (isSuspiciousEqualsCall(context, node)) {
            report(
                context,
                node,
                "Calling `equals()` on a type that does not override `Object.equals()` can produce incorrect DiffUtil results.",
            )
        }
    }

    private fun insideAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UMethod) {
                if (current.name != "areContentsTheSame") return false
                val containingClass = current.getContainingClass() ?: return false
                return DIFF_UTIL_CALLBACKS.any {
                    context.evaluator.extendsClass(containingClass.psi, it, false)
                }
            }
            current = current.parent
        }
        return false
    }

    private fun isIdentityComparison(node: UBinaryExpression): Boolean {
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
            node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return true
        }

        val source = node.sourcePsi
        if (source is PsiBinaryExpression) {
            val token = source.operationSign.tokenType
            if (token == JavaTokenType.EQEQ || token == JavaTokenType.NE) {
                val leftType = node.leftOperand.getExpressionType()
                val rightType = node.rightOperand.getExpressionType()
                if (leftType == null || rightType == null) return true
                return leftType !is PsiPrimitiveType && rightType !is PsiPrimitiveType
            }
        }
        return false
    }

    private fun isSuspiciousEqualsCall(context: JavaContext, node: UCallExpression): Boolean {
        if (node.methodName != "equals" || node.valueArgumentCount != 1) return false

        val method = node.resolve() ?: return false
        if (method.containingClass?.qualifiedName != "java.lang.Object") return false

        val receiver = node.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        val psiClass = resolveClassForType(context, receiverType, node) ?: return false

        return !overridesEquals(psiClass)
    }

    private fun resolveClassForType(
        context: JavaContext,
        type: PsiType,
        node: UCallExpression,
    ): PsiClass? {
        return when (type) {
            is PsiClassType -> {
                val resolved = type.resolve()
                if (resolved is PsiTypeParameter) {
                    val callbackClass = findContainingCallbackClass(node) ?: return null
                    getItemTypeClass(callbackClass)
                } else {
                    resolved
                }
            }
            else -> null
        }
    }

    private fun findContainingCallbackClass(node: UElement): UClass? {
        var current: UElement? = node
        while (current != null) {
            if (current is UClass) return current
            current = current.parent
        }
        return null
    }

    private fun getItemTypeClass(callbackClass: UClass): PsiClass? {
        val psiClass = callbackClass.psi ?: return null
        val superTypes =
            (psiClass.extendsList?.referencedTypes?.toList().orEmpty()) +
                    (psiClass.implementsList?.referencedTypes?.toList().orEmpty())

        for (superType in superTypes) {
            val superClass = superType.resolve() ?: continue
            if (superClass.qualifiedName in DIFF_UTIL_CALLBACKS) {
                val typeArguments = superType.parameters
                if (typeArguments.isNotEmpty()) {
                    val first = typeArguments[0]
                    if (first is PsiClassType) {
                        return first.resolve()
                    }
                }
            }
        }
        return null
    }

    private fun overridesEquals(psiClass: PsiClass?): Boolean {
        if (psiClass == null) return false

        var current: PsiClass? = psiClass
        while (current != null && current.qualifiedName != "java.lang.Object") {
            for (method in current.methods) {
                if (method.name == "equals" &&
                    method.parameterList.parametersCount == 1 &&
                    method.parameterList.parameters[0].type.canonicalText == "java.lang.Object"
                ) {
                    return true
                }
            }
            current = current.superClass
        }
        return false
    }

    private fun report(context: JavaContext, node: UElement, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }
}