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
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        DIFF_UTIL_CALLBACK,
        DIFF_UTIL_ITEM_CALLBACK
    )

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
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
            node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            val leftType = node.leftOperand.getExpressionType() ?: return
            if (isObjectType(leftType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: Did you mean `.equals()` instead of `==`?"
                )
            }
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "equals") return

        val psiMethod = node.resolve() ?: return
        val containingClass = psiMethod.containingClass ?: return

        if (!definesEquals(containingClass)) {
            val receiverType = node.receiverType ?: return
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `${receiverType.presentableText}` does not " +
                    "define an `equals()` method"
            )
        }
    }

    private fun isObjectType(type: PsiType): Boolean {
        val canonicalText = type.canonicalText
        return !isPrimitive(canonicalText)
    }

    private fun isPrimitive(canonicalText: String): Boolean {
        return when (canonicalText) {
            "int", "long", "short", "byte", "char", "float", "double", "boolean" -> true
            else -> false
        }
    }

    private fun definesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.qualifiedName == "java.lang.Object") return false

        val methods = psiClass.findMethodsByName("equals", false)
        for (method in methods) {
            val params = method.parameterList.parameters
            if (params.size == 1 && params[0].type.canonicalText == "java.lang.Object") {
                return true
            }
        }

        val superClass = psiClass.superClass ?: return false
        if (superClass.qualifiedName == "java.lang.Object") return false

        return definesEquals(superClass)
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        // Handled via the visitor inside visitClass/checkMethod
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // Handled via the visitor inside visitClass/checkMethod
    }
}