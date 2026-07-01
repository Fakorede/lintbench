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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

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
            explanation = "areContentsTheSame is used by DiffUtil to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "android.support.v7.util.DiffUtil.Callback"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Expression scanning is handled by visitBinaryExpression and visitCallExpression
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression): Boolean {
        if (!isInsideAreContentsTheSame(context, node)) return true
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS || node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Using identity equals (`===`) in `areContentsTheSame` is usually incorrect; use structural equals (`==`) instead."
            )
        }
        return true
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression): Boolean {
        if (!isInsideAreContentsTheSame(context, node)) return true
        if (node.methodName == "equals" && node.valueArgumentCount == 1) {
            val receiver = node.receiver
            if (receiver != null) {
                val receiverType = receiver.getExpressionType()
                if (receiverType != null) {
                    val psiClass = context.evaluator.getTypeClass(receiverType)
                    if (psiClass != null && !hasCustomEquals(psiClass)) {
                        context.report(
                            ISSUE, node, context.getLocation(node),
                            "Calling `equals` on `${psiClass.name}` which does not override `equals`; this will fall back to identity comparison."
                        )
                    }
                }
            }
        }
        return true
    }

    private fun isInsideAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        val method = node.getParentOfType(UMethod::class.java, true) ?: return false
        if (method.name != "areContentsTheSame") return false
        val uClass = method.getParentOfType(UClass::class.java, true) ?: return false
        val psiClass = uClass.javaPsi ?: return false
        return context.evaluator.extendsClass(psiClass, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
               context.evaluator.extendsClass(psiClass, "android.support.v7.util.DiffUtil.Callback", false)
    }

    private fun hasCustomEquals(psiClass: PsiClass): Boolean {
        return psiClass.allMethods.any { method ->
            method.name == "equals" &&
            method.parameterList.parametersCount == 1 &&
            method.containingClass?.qualifiedName != "java.lang.Object" &&
            method.containingClass?.qualifiedName != "kotlin.Any"
        }
    }
}