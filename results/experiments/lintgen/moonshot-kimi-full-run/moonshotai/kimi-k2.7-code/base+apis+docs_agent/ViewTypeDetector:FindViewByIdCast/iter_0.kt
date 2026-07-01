package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Location
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastUtils

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which means
                that most of the time you can leave out explicit casts and just assign the result
                of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code
                to not compile without explicit casts. This lint check looks for these scenarios
                and suggests casts to be added now such that the code will continue to compile if
                the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FIND_VIEW_BY_ID_OWNER_CLASSES = listOf(
            "android.app.Activity",
            "android.view.View",
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        if (!isFindViewById(context, method)) {
            return
        }

        if (call.valueArguments.size != 1) {
            return
        }

        if (isExplicitlyCast(call)) {
            return
        }

        val expectedType = getExpectedViewType(context, call) ?: return
        val expectedClass = expectedType.resolve() ?: return

        if (!context.evaluator.extendsClass(expectedClass, "android.view.View", false)) {
            return
        }

        if ("android.view.View" == expectedClass.qualifiedName) {
            return
        }

        val message = "Add explicit cast to ${expectedClass.name}"
        context.report(ISSUE, call, context.getLocation(call), message)
    }

    private fun isFindViewById(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return FIND_VIEW_BY_ID_OWNER_CLASSES.any { className ->
            context.evaluator.extendsClass(containingClass, className, false)
        }
    }

    private fun isExplicitlyCast(call: UCallExpression): Boolean {
        val parent = call.uastParent
        return parent is UTypeCastExpression && parent.operand == call
    }

    private fun getExpectedViewType(context: JavaContext, call: UCallExpression): PsiClassType? {
        val parent = call.uastParent ?: return null

        val expectedType: PsiType? = when (parent) {
            is UTypeCastExpression -> null
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    parent.leftOperand.getExpressionType()
                } else {
                    null
                }
            }
            is UReturnExpression -> {
                val containingMethod = UastUtils.getParentOfType(call, UMethod::class.java)
                containingMethod?.returnType
            }
            is UCallExpression -> {
                val index = parent.valueArguments.indexOf(call)
                if (index >= 0) {
                    val psiMethod = parent.resolve() ?: return null
                    val params = psiMethod.parameterList.parameters
                    if (index < params.size) params[index].type else null
                } else {
                    null
                }
            }
            else -> null
        }

        return expectedType as? PsiClassType
    }
}