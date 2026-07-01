package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals (`===` / `==` for \
                references) instead of structural equals, or calling `equals` on a class that \
                has not implemented it, weird visual artifacts can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://issuetracker.google.com/116789824"
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CALLBACK, DIFF_UTIL_CALLBACK_OLD)
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

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS ||
            operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: did you mean to call `equals()` instead of `===`?"
            )
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        // Resolve the method being called
        val resolvedMethod: PsiMethod = node.resolve() ?: return

        // Get the containing class of the equals method
        val containingClass: PsiClass = resolvedMethod.containingClass ?: return

        // Check if equals is defined on Object (i.e., not overridden)
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName == "java.lang.Object") {
            // The class hasn't overridden equals — find the receiver type
            val receiver = node.receiver
            val receiverType = receiver?.getExpressionType()
            val receiverClass = receiverType?.let {
                context.evaluator.getTypeClass(it)
            }

            val className = receiverClass?.qualifiedName ?: receiverClass?.name ?: "this class"

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `equals()` is not implemented in `$className`; " +
                        "this will use identity equality"
            )
        }
    }
}