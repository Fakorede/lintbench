package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        DIFF_UTIL_CALLBACK,
        DIFF_UTIL_CALLBACK_OLD,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val method = declaration.methods.firstOrNull { it.name == ARE_CONTENTS_THE_SAME } ?: return
        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                checkBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    override fun visitBinaryExpression(node: UBinaryExpression) {
        // Not used directly; logic is in the visitor above
    }

    override fun visitCallExpression(node: UCallExpression) {
        // Not used directly; logic is in the visitor above
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
            node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            val leftType = node.leftOperand.getExpressionType()
            if (leftType != null && !isPrimitive(leftType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: did you mean `.equals()` instead of `===`?",
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return

        // Resolve the receiver type to a PsiClass
        val psiClass = resolveClass(receiverType) ?: return

        if (!hasCustomEquals(psiClass)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `${psiClass.name}` does not override `equals()`.",
            )
        }
    }

    private fun isPrimitive(type: PsiType): Boolean {
        return type is com.intellij.psi.PsiPrimitiveType
    }

    private fun resolveClass(type: PsiType): PsiClass? {
        if (type !is com.intellij.psi.PsiClassType) return null
        return type.resolve()
    }

    private fun hasCustomEquals(psiClass: PsiClass): Boolean {
        // Walk the class hierarchy (excluding Object) looking for an equals override
        var current: PsiClass? = psiClass
        while (current != null) {
            val qualifiedName = current.qualifiedName
            // Stop at java.lang.Object — it has equals but not a meaningful override
            if (qualifiedName == "java.lang.Object") {
                return false
            }
            val hasEquals = current.findMethodsByName("equals", false).any { method ->
                isEqualsMethod(method)
            }
            if (hasEquals) {
                return true
            }
            current = current.superClass
        }
        return false
    }

    private fun isEqualsMethod(method: PsiMethod): Boolean {
        val params = method.parameterList.parameters
        if (params.size != 1) return false
        val paramType = params[0].type
        return paramType.equalsToText("java.lang.Object") || paramType.equalsToText("Object")
    }
}