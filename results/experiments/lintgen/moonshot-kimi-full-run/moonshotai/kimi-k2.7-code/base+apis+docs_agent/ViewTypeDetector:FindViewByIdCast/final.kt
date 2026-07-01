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
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.resolveToUElement

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById", "requireViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isFindViewByIdMethod(context, method)) return
        if (isAlreadyCast(node)) return

        val target = getTargetType(node)
        if (target == null || !isAssignableToView(target)) {
            // No applicable target type; need explicit cast.
            // But what type? Use View as fallback.
            report(context, node, "Add explicit cast to View")
        }
    }

    private fun isFindViewByIdMethod(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)
                || evaluator.isMemberInSubClassOf(method, "android.view.View", false)
                || evaluator.isMemberInSubClassOf(method, "android.app.Fragment", false)
                || evaluator.isMemberInSubClassOf(method, "android.support.v4.app.Fragment", false)
                || evaluator.isMemberInSubClassOf(method, "androidx.fragment.app.Fragment", false)
    }

    private fun isAlreadyCast(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        return parent is UBinaryExpressionWithType
                && parent.operationKind == UastBinaryExpressionWithTypeKind.CAST
    }

    private fun getTargetType(node: UCallExpression): PsiType? {
        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        return when (parent) {
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    val left = parent.leftOperand
                    if (left is UReferenceExpression) {
                        (left.resolve() as? PsiVariable)?.type
                    } else null
                } else null
            }
            is UReturnExpression -> {
                val method = parent.getParentOfType(UMethod::class.java)
                method?.returnType
            }
            is UCallExpression -> {
                // argument: find parameter type
                ...
            }
            is UQualifiedReferenceExpression -> {
                // receiver: no target type
                null
            }
            else -> null
        }
    }
}