package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun beforeCheckFile(context: Context) {
        if (context !is JavaContext) return
        val uFile = context.uastFile ?: return
        uFile.accept(DiffUtilVisitor(context))
    }

    private class DiffUtilVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val method = node.getParentOfType(UMethod::class.java, true) ?: return true
            if (!isAreContentsTheSame(method)) return true

            val expr = unwrapParens(node.returnExpression) ?: return true
            checkIdentityEquals(method, expr)
            checkEqualsCall(method, expr)
            return true
        }
    }

    private fun isAreContentsTheSame(method: UMethod): Boolean {
        if (method.name != "areContentsTheSame") return false

        val returnType = method.returnType ?: return false
        if (!returnType.equalsToText("boolean") && !returnType.equalsToText("java.lang.Boolean")) return false

        if (method.uastParameters.size != 2) return false

        val containingClass = method.javaPsi?.containingClass
            ?: method.getParentOfType(UClass::class.java, false)?.javaPsi
            ?: return false

        val evaluator = context.evaluator // Wait, context is not in companion or member. Need to pass.
        ...
    }
}