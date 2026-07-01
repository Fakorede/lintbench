package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
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
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://issuetracker.google.com/116789824"
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CALLBACK, DIFF_UTIL_ITEM_CALLBACK)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        for (method in methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                this@DiffUtilDetector.visitBinaryExpression(context, node)
                return false
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                this@DiffUtilDetector.visitCallExpression(context, node)
                return false
            }
        })
    }

    fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            val leftType = node.leftOperand.getExpressionType() ?: return
            if (leftType == PsiType.BOOLEAN ||
                leftType == PsiType.BYTE ||
                leftType == PsiType.CHAR ||
                leftType == PsiType.DOUBLE ||
                leftType == PsiType.FLOAT ||
                leftType == PsiType.INT ||
                leftType == PsiType.LONG ||
                leftType == PsiType.SHORT
            ) {
                return
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: Did you mean `.equals()` instead of `===`?"
            )
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        val psiMethod = node.resolve() ?: return

        val containingClass = psiMethod.containingClass ?: return

        // Check if the equals method is defined on Object (i.e., not overridden)
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName == "java.lang.Object") {
            val receiverType = node.receiver?.getExpressionType() ?: run {
                // no explicit receiver; get the type from the dispatch receiver
                node.receiverType
            } ?: return

            val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return

            // Check if this class has overridden equals
            val equalsMethod = receiverClass.findMethodsByName("equals", true)
            val hasOverriddenEquals = equalsMethod.any { m ->
                val cls = m.containingClass?.qualifiedName
                cls != null && cls != "java.lang.Object"
            }

            if (!hasOverriddenEquals) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: `${receiverClass.name}` does not override `equals()`"
                )
            }
        }
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        // This override is intentionally empty; logic is in the internal method called from the visitor
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // This override is intentionally empty; logic is in the internal method called from the visitor
    }
}