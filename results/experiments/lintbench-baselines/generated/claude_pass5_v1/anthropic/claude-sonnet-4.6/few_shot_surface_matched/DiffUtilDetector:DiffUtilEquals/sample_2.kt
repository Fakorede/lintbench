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
import org.jetbrains.uast.UElement
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
                checkBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        // This is called for binary expressions outside of specific class context;
        // actual checking is done in visitClass via the visitor above.
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // This is called for call expressions outside of specific class context;
        // actual checking is done in visitClass via the visitor above.
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator != UastBinaryOperator.IDENTITY_EQUALS &&
            node.operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return
        }

        val leftType = node.leftOperand.getExpressionType() ?: return
        if (leftType == PsiType.NULL || leftType == PsiType.BOOLEAN ||
            leftType == PsiType.BYTE || leftType == PsiType.CHAR ||
            leftType == PsiType.SHORT || leftType == PsiType.INT ||
            leftType == PsiType.LONG || leftType == PsiType.FLOAT ||
            leftType == PsiType.DOUBLE
        ) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Suspicious equality check: Did you mean `.equals()` instead of `===` / `!==`?"
        )
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        val method: PsiMethod = node.resolve() ?: return

        val containingClass = method.containingClass ?: return

        // If equals is defined on Object itself, the class hasn't overridden equals
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName == "java.lang.Object") {
            val receiverType = node.receiverType
            val receiverTypeName = receiverType?.canonicalText ?: return

            // Don't warn for well-known types that have equals defined
            if (receiverTypeName.startsWith("java.") ||
                receiverTypeName.startsWith("kotlin.") ||
                receiverTypeName.startsWith("android.")
            ) {
                return
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node as UElement),
                "Suspicious equality check: `equals()` is not implemented in `$receiverTypeName`"
            )
        }
    }
}