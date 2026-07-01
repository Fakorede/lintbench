package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return
                val cls = node.getParentOfType(UClass::class.java, false) ?: return
                if (!isDiffUtilCallback(context, cls)) return
                if (!isAreContentsTheSame(node)) return
                node.accept(DiffUtilVisitor(context))
            }
        }
    }

    private fun isDiffUtilCallback(context: JavaContext, cls: UClass): Boolean {
        return context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_CALLBACK, false) ||
                context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_CALLBACK_V7, false) ||
                context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_ITEM_CALLBACK, false) ||
                context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_ITEM_CALLBACK_V7, false)
    }

    private fun isAreContentsTheSame(method: UMethod): Boolean {
        val returnType = method.returnType ?: return false
        if (!returnType.equalsToText("boolean") && !returnType.equalsToText("java.lang.Boolean")) {
            return false
        }
        return method.uastParameters.size == 2
    }

    private class DiffUtilVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val expr = skipParentheses(node.returnExpression) ?: return true
            checkExpression(expr)
            return true
        }

        private fun checkExpression(expr: UExpression) {
            val unwrapped = skipParentheses(expr) ?: return
            when (unwrapped) {
                is UBinaryExpression -> checkBinaryExpression(unwrapped)
                is UCallExpression -> checkEqualsCall(unwrapped)
                is UPrefixExpression -> {
                    if (unwrapped.operator == UastPrefixOperator.NOT) {
                        checkExpression(unwrapped.operand)
                    }
                }
            }
        }

        private fun checkBinaryExpression(expr: UBinaryExpression) {
            val op = expr.operator
            if (op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                reportIdentityEquals(expr)
                return
            }
            if (op != UastBinaryOperator.EQUALS && op != UastBinaryOperator.NOT_EQUALS) {
                return
            }
            if (context.file !is PsiJavaFile) {
                return
            }
            if (isPrimitive(expr.leftOperand) || isPrimitive(expr.rightOperand)) {
                return
            }
            reportIdentityEquals(expr)
        }

        private fun checkEqualsCall(expr: UCallExpression) {
            if (expr.methodName != "equals") return
            if (expr.valueArguments.size != 1) return
            val resolved = expr.resolve() ?: return
            val containingClass = resolved.containingClass ?: return
            if (containingClass.qualifiedName != "java.lang.Object") return
            context.report(
                ISSUE,
                expr,
                context.getLocation(expr),
                "Calling `equals()` on a class that does not override `equals()` can produce incorrect DiffUtil results; use a proper equality check"
            )
        }

        private fun reportIdentityEquals(expr: UExpression) {
            context.report(
                ISSUE,
                expr,
                context.getLocation(expr),
                "Using identity equality in `areContentsTheSame()`; use `equals()` or a structural comparison instead"
            )
        }

        private fun isPrimitive(expr: UExpression): Boolean {
            return expr.getExpressionType()?.isPrimitive == true
        }

        private fun skipParentheses(expr: UExpression?): UExpression? {
            var e = expr
            while (e is UParenthesizedExpression) {
                e = e.expression
            }
            return e
        }
    }

    companion object {
        private const val CLASS_DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val CLASS_DIFF_UTIL_CALLBACK_V7 = "android.support.v7.util.DiffUtil.Callback"
        private const val CLASS_DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val CLASS_DIFF_UTIL_ITEM_CALLBACK_V7 = "android.support.v7.util.DiffUtil.ItemCallback"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality comparison",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Implementing it with identity equality (`==` or `===`) or calling `equals()` on a class that does not override `equals()` can lead to incorrect diffs and visual artifacts. Use a proper structural equality check.
            """.trimIndent(),
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}