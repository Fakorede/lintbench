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
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastUtils

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to decide whether two items represent the same content.
                Using identity equality (`==` in Java, `===` in Kotlin) or calling `equals()` on a class that does not
                override `Object.equals()` can produce incorrect diffs and visual artifacts.
                Compare the actual contents instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val DIFF_UTIL_CALLBACKS = listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback",
        )
    }

    override fun applicableSuperClasses(): List<String>? = DIFF_UTIL_CALLBACKS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // The individual expression checks are performed by the UAST visitor methods below.
    }

    override fun visitBinaryExpression(context: JavaContext, expression: UBinaryExpression) {
        if (!isInAreContentsTheSame(context, expression)) return

        if (expression.operator != UastBinaryOperator.IDENTITY_EQUALS &&
            expression.operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return
        }

        // DiffUtil.Callback compares primitive positions; primitive == is fine.
        if (isPrimitive(expression.leftOperand.getExpressionType()) &&
            isPrimitive(expression.rightOperand.getExpressionType())
        ) {
            return
        }

        context.report(
            ISSUE,
            expression,
            context.getLocation(expression),
            "Suspicious identity comparison in `areContentsTheSame`; use `.equals()` (or `==` in Kotlin) instead"
        )
    }

    override fun visitCallExpression(context: JavaContext, call: UCallExpression) {
        if (!isInAreContentsTheSame(context, call)) return

        val method = call.resolve() ?: return
        if (method.name != "equals" || !isObjectEquals(method)) return

        if (method.containingClass?.qualifiedName == "java.lang.Object") {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Suspicious `equals()` call in `areContentsTheSame`; the receiver type does not override `Object.equals()`"
            )
        }
    }

    private fun isInAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        val method = UastUtils.getParentOfType(node, UMethod::class.java) ?: return false
        if (method.name != "areContentsTheSame") return false
        val clazz = UastUtils.getParentOfType(method, UClass::class.java) ?: return false
        return DIFF_UTIL_CALLBACKS.any { context.evaluator.extendsClass(clazz, it, false) }
    }

    private fun isObjectEquals(method: PsiMethod): Boolean {
        if (method.parameterList.parameters.size != 1) return false
        val paramType = method.parameterList.parameters[0].type.canonicalText
        return paramType == "java.lang.Object" || paramType == "Object"
    }

    private fun isPrimitive(type: PsiType?): Boolean = type is PsiPrimitiveType
}