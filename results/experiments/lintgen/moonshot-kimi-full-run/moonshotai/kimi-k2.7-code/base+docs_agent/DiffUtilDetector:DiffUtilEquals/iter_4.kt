package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
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
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java, UReturnExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UastVisitor? {
        return DiffUtilVisitor(context)
    }

    private class DiffUtilVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        private var targetDepth = 0

        override fun visitMethod(node: UMethod): Boolean {
            if (targetDepth == 0 && isTargetMethod(context, node)) {
                targetDepth = 1
            } else if (targetDepth > 0) {
                targetDepth++
            }
            return false
        }

        override fun afterVisitMethod(node: UMethod) {
            if (targetDepth > 0) {
                targetDepth--
            }
        }

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (targetDepth == 0) return false
            val expr = node.returnExpression?.let { skipParentheses(it) } ?: return false
            checkExpression(expr)
            return true
        }

        private fun checkExpression(expr: UExpression) {
            when (val e = skipParentheses(expr)) {
                is UBinaryExpression -> checkBinary(e)
                is UCallExpression -> checkEqualsCall(e)
                is UPrefixExpression -> checkExpression(e.operand)
            }
        }

        private fun checkBinary(expr: UBinaryExpression) {
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
            val resolved = expr.resolve() as? PsiMethod ?: return
            val containingClass = resolved.containingClass ?: return
            val qName = containingClass.qualifiedName
            if (qName != "java.lang.Object" && qName != "kotlin.Any") return
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
            return expr.getExpressionType() is PsiPrimitiveType
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

        private fun isTargetMethod(context: JavaContext, method: UMethod): Boolean {
            if (method.name != "areContentsTheSame") return false
            val returnType = method.returnType ?: return false
            if (!returnType.equalsToText("boolean") && !returnType.equalsToText("java.lang.Boolean")) {
                return false
            }
            if (method.uastParameters.size != 2) return false
            val cls = method.getParentOfType(UClass::class.java, false) ?: return false
            return isDiffUtilCallback(context, cls)
        }

        private fun isDiffUtilCallback(context: JavaContext, cls: UClass): Boolean {
            return context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_CALLBACK, false) ||
                    context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_CALLBACK_V7, false) ||
                    context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_ITEM_CALLBACK, false) ||
                    context.evaluator.extendsClass(cls, CLASS_DIFF_UTIL_ITEM_CALLBACK_V7, false)
        }

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