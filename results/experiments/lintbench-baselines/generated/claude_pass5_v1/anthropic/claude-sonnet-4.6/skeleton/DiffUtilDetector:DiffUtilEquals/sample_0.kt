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
import org.jetbrains.uast.getParentOfType
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

    override fun visitBinaryExpression(
        context: JavaContext,
        node: UBinaryExpression,
    ) {
        checkBinaryExpression(context, node)
    }

    override fun visitCallExpression(
        context: JavaContext,
        node: UCallExpression,
    ) {
        checkCallExpression(context, node)
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        // Check if we're inside areContentsTheSame
        if (!isInsideAreContentsTheSame(node)) return

        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS ||
            operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            val leftType = node.leftOperand.getExpressionType()
            if (leftType != null && !isPrimitive(leftType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: Did you mean `.equals()` instead of `==`?",
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        // Check if we're inside areContentsTheSame
        if (!isInsideAreContentsTheSame(node)) return

        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        val method: PsiMethod = node.resolve() ?: return
        val containingClass: PsiClass = method.containingClass ?: return

        // Check if the equals method is from Object (i.e., not overridden)
        if (containingClass.qualifiedName == "java.lang.Object") {
            val receiverType = node.receiver?.getExpressionType()
            if (receiverType != null && !isPrimitive(receiverType)) {
                val typeName = receiverType.presentableText
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: `$typeName` does not override `equals()`",
                )
            }
        }
    }

    private fun isInsideAreContentsTheSame(node: org.jetbrains.uast.UElement): Boolean {
        val method = node.getParentOfType<UMethod>(strict = true) ?: return false
        return method.name == ARE_CONTENTS_THE_SAME
    }

    private fun isPrimitive(type: PsiType): Boolean {
        return type is com.intellij.psi.PsiPrimitiveType
    }
}