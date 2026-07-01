package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!isAreContentsTheSame(context, node)) return
                node.uastBody?.accept(ReturnVisitor(context, node))
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

        val evaluator = context.evaluator
        return evaluator.isMemberInSubClass(
            method,
            "androidx.recyclerview.widget.DiffUtil.Callback",
            false
        ) || evaluator.isMemberInSubClass(
            method,
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            false
        ) || evaluator.isMemberInSubClass(
            method,
            "android.support.v7.util.DiffUtil.Callback",
            false
        ) || evaluator.isMemberInSubClass(
            method,
            "android.support.v7.util.DiffUtil.ItemCallback",
            false
        )
    }

    private class ReturnVisitor(
        private val context: JavaContext,
        private val method: UMethod
    ) : AbstractUastVisitor() {

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (node.getParentOfType(UMethod::class.java, true) != method) {
                return super.visitReturnExpression(node)
            }

            val expr = node.returnExpression?.skipParentheses() ?: return super.visitReturnExpression(node)
            checkIdentityEquals(expr)
            checkEqualsCall(expr)

            return super.visitReturnExpression(node)
        }

        private fun checkIdentityEquals(expr: UExpression) {
            val binExpr = expr as? UBinaryExpression ?: return
            if (binExpr.operator != UastBinaryOperator.IDENTITY_EQUALS &&
                binExpr.operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                return
            }

            val params = method.parameterElements()
            val left = binExpr.leftOperand.skipParentheses()
            val right = binExpr.rightOperand.skipParentheses()

            if (params.any { context.evaluator.areElementsEquivalent(it, resolveReference(left)) } &&
                params.any { context.evaluator.areElementsEquivalent(it, resolveReference(right)) }
            ) {
                context.report(
                    ISSUE,
                    expr,
                    context.getLocation(expr),
                    "Using identity equality (`==`/`===`) in `areContentsTheSame`; consider using `equals()`"
                )
            }
        }

        private fun checkEqualsCall(expr: UExpression) {
            val call = expr as? UCallExpression ?: return
            if (call.methodName != "equals" || call.valueArguments.size != 1) return

            val params = method.parameterElements()
            val receiver = call.receiver?.skipParentheses()
            val arg = call.valueArguments[0].skipParentheses()

            val isParamReceiver = params.any {
                context.evaluator.areElementsEquivalent(it, resolveReference(receiver))
            }
            val isParamArg = params.any {
                context.evaluator.areElementsEquivalent(it, resolveReference(arg))
            }

            if (!isParamReceiver || !isParamArg) return

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
            var cls = (type as? PsiClassType)?.resolve() ?: return true
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
    }

    private fun UMethod.parameterElements(): List<PsiElement> =
        uastParameters.map { it.sourcePsi ?: it.javaPsi }

    private fun resolveReference(expr: UExpression?): PsiElement? {
        return when (expr) {
            is USimpleNameReferenceExpression -> expr.resolve()
            is UQualifiedReferenceExpression -> expr.resolve()
            else -> null
        }
    }

    private fun UExpression?.skipParentheses(): UExpression? {
        var e = this
        while (e is UParenthesizedExpression) {
            e = e.expression
        }
        return e
    }

    companion object {
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