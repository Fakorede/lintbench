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

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.Callback"
        private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_ITEM_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        DIFF_UTIL_CALLBACK,
        DIFF_UTIL_CALLBACK_OLD,
        DIFF_UTIL_ITEM_CALLBACK,
        DIFF_UTIL_ITEM_CALLBACK_OLD,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        for (method in methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
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
        }
    }

    override fun visitBinaryExpression(node: UBinaryExpression) {
        // Not used directly; handled via visitor in visitClass
    }

    override fun visitCallExpression(node: UCallExpression) {
        // Not used directly; handled via visitor in visitClass
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
                    "Suspicious equality check: Did you mean `.equals()` instead of `===`?",
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "equals") return

        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return

        if (isPrimitive(receiverType)) return

        val psiClass = findPsiClass(context, receiverType) ?: return

        if (!hasCustomEquals(context, psiClass)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `${psiClass.name}` does not implement `equals()`",
            )
        }
    }

    private fun isPrimitive(type: PsiType): Boolean {
        return type == PsiType.INT ||
            type == PsiType.LONG ||
            type == PsiType.DOUBLE ||
            type == PsiType.FLOAT ||
            type == PsiType.BOOLEAN ||
            type == PsiType.CHAR ||
            type == PsiType.BYTE ||
            type == PsiType.SHORT
    }

    private fun findPsiClass(context: JavaContext, type: PsiType): PsiClass? {
        val canonicalText = type.canonicalText
        // Strip generics
        val rawType = canonicalText.substringBefore("<")
        return context.evaluator.findClass(rawType)
    }

    private fun hasCustomEquals(context: JavaContext, psiClass: PsiClass): Boolean {
        // Check if it's Object itself — Object has equals but it's identity-based
        if (psiClass.qualifiedName == "java.lang.Object") return false

        // Check if this class directly declares equals
        val directEquals = psiClass.findMethodsByName("equals", false)
        for (method in directEquals) {
            if (isEqualsMethod(method)) return true
        }

        // Check superclasses (excluding Object)
        val superClass = psiClass.superClass ?: return false
        if (superClass.qualifiedName == "java.lang.Object") return false

        return hasCustomEquals(context, superClass)
    }

    private fun isEqualsMethod(method: PsiMethod): Boolean {
        if (method.name != "equals") return false
        val params = method.parameterList.parameters
        if (params.size != 1) return false
        val paramType = params[0].type
        return paramType.canonicalText == "java.lang.Object"
    }
}