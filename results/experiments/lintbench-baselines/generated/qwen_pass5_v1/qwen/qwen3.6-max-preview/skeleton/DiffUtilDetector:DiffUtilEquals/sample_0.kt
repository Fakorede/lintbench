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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod
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
            explanation = "`areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val DIFF_UTIL_CLASSES = listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun applicableSuperClasses(): List<String>? = DIFF_UTIL_CLASSES

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(
        UClass::class.java,
        UBinaryExpression::class.java,
        UCallExpression::class.java
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Detection logic is handled in expression visitors.
        // This method is triggered by applicableSuperClasses to limit scanning scope.
    }

    private fun isInsideTargetMethod(context: JavaContext, node: UElement): Boolean {
        val method = node.getContainingUMethod() ?: return false
        if (method.name != "areContentsTheSame" || method.parameterList.parametersCount != 2) {
            return false
        }
        val cls = method.getParentOfType<UClass>(true) ?: return false
        return DIFF_UTIL_CLASSES.any { context.evaluator.extendsClass(cls, it, true) }
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!isInsideTargetMethod(context, node)) return

        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Using identity equals (===) in areContentsTheSame; use structural equals (==) instead"
            )
            return
        }

        if (operator == UastBinaryOperator.EQUALS) {
            if (context.isJava) {
                val leftType = node.leftOperand.getExpressionType()
                if (leftType != null && !leftType.isPrimitive) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Using == on objects in areContentsTheSame compares references; use .equals() instead"
                    )
                }
            } else {
                checkEqualsOverride(context, node.leftOperand.getExpressionType(), node)
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!isInsideTargetMethod(context, node)) return

        if (node.methodName == "equals" && node.valueArgumentCount == 1) {
            val receiverType = node.receiver?.getExpressionType()
            checkEqualsOverride(context, receiverType, node)
        }
    }

    private fun checkEqualsOverride(context: JavaContext, type: PsiType?, node: UElement) {
        if (type == null) return
        val psiClass: PsiClass? = context.evaluator.getTypeClass(type)
        if (psiClass == null) return

        val declaresEquals = psiClass.findMethodsByName("equals", false).isNotEmpty()
        if (!declaresEquals) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Calling equals on ${psiClass.name} which does not override equals(); this will compare references"
            )
        }
    }
}