package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UReturnExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitReturnExpression(node: UReturnExpression) {
                val method = node.getParentOfType(UMethod::class.java, true) ?: return
                if (!isAreContentsTheSame(context, method)) return

                val expr = unwrapParens(node.returnExpression) ?: return
                checkIdentityEquals(context, method, expr)
                checkEqualsCall(context, method, expr)
            }
        }

    private fun isAreContentsTheSame(context: JavaContext, method: UMethod): Boolean {
        if (method.name != "areContentsTheSame") return false

        val returnType = method.returnType ?: return false
        if (!returnType.equalsToText("boolean") &&
            !returnType.equalsToText("java.lang.Boolean")
        ) {
            return false
        }

        if (method.uastParameters.size != 2) return false

        val containingClass = method.javaPsi?.containingClass
            ?: method.getParentOfType(UClass::class.java, false)?.javaPsi
            ?: return false

        val evaluator = context.evaluator
        return DIFF_UTIL_CALLBACKS.any { evaluator.extendsClass(containingClass, it, false) }
    }

    private fun checkIdentityEquals(
        context: JavaContext,
        method: UMethod,
        expr: UExpression
    ) {
        val binExpr = expr as? UBinaryExpression ?: return
        if (binExpr.operator != UastBinaryOperator.IDENTITY_EQUALS &&
            binExpr.operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return
        }

        val paramNames = method.uastParameters.mapNotNull { it.name }.toSet()
        val leftName = (unwrapParens(binExpr.leftOperand) as? USimpleNameReferenceExpression)?.identifier
        val rightName = (unwrapParens(binExpr.rightOperand) as? USimpleNameReferenceExpression)?.identifier

        if (leftName in paramNames && rightName in paramNames) {
            context.report(
                ISSUE,
                expr,
                context.getLocation(expr),
                "Using identity equality (`==`/`===`) in `areContentsTheSame`; consider using `equals()`"
            )
        }
    }

    private fun checkEqualsCall(
        context: JavaContext,
        method: UMethod,
        expr: UExpression
    ) {
        val call = expr as? UCallExpression ?: return
        if (call.methodName != "equals" || call.valueArguments.size != 1) return

        val paramNames = method.uastParameters.mapNotNull { it.name }.toSet()
        val receiverName = (unwrapParens(call.receiver) as? USimpleNameReferenceExpression)?.identifier
        val argName = (unwrapParens(call.valueArguments[0]) as? USimpleNameReferenceExpression)?.identifier

        if (receiverName !in paramNames || argName !in paramNames) return

        val receiverType = call.receiver?.getExpressionType()
        if (!hasOverriddenEquals(receiverType)) {
            context.report(
                ISSUE,
                expr,
                context.getLocation(expr),
                "Calling `equals()` on a class that does not override `equals()`; this behaves like identity equality"
            )
        }
    }

    private fun hasOverriddenEquals(type: PsiType?): Boolean {
        var cls: PsiClass? = (type as? PsiClassType)?.resolve() ?: return true
        while (cls != null && cls.qualifiedName != "java.lang.Object") {
            for (method in cls.findMethodsByName("equals", false)) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1 &&
                    parameters[0].type.canonicalText == "java.lang.Object"
                ) {
                    return true
                }
            }
            cls = cls.superClass
        }
        return false
    }

    companion object {
        private val DIFF_UTIL_CALLBACKS = listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method \
                is implemented incorrectly, such as using identity equals instead of equals, \
                or calling equals on a class that has not implemented it, weird visual \
                artifacts can occur.
                """.trimIndent(),
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

private fun unwrapParens(expr: UExpression?): UExpression? {
    var e = expr
    while (e is UParenthesizedExpression) {
        e = e.expression
    }
    return e
}