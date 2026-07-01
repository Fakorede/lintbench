package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isFindViewById(context, method)) return
        if (method.typeParameters.isEmpty()) return
        if (isAlreadyCast(node)) return

        val expectedType = getExpectedType(context, node) ?: return
        if (!isStrictViewSubclass(context, expectedType)) return

        report(context, node, expectedType)
    }

    private fun isFindViewById(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.isMemberInClass(method, "android.view.View")
                || evaluator.isMemberInClass(method, "android.app.Activity")
                || evaluator.isMemberInClass(method, "android.app.Fragment")
                || evaluator.isMemberInClass(method, "android.support.v4.app.Fragment")
                || evaluator.isMemberInClass(method, "androidx.fragment.app.Fragment")
                || evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatActivity")
    }

    private fun isStrictViewSubclass(context: JavaContext, type: PsiType?): Boolean {
        if (type == null || type == PsiType.VOID) return false
        val classType = type as? PsiClassType ?: return false
        val psiClass = classType.resolve() ?: return false
        return context.evaluator.extendsClass(psiClass, "android.view.View", true)
    }

    private fun isAlreadyCast(node: UCallExpression): Boolean {
        val parent = node.uastParent ?: return false
        return when (parent) {
            is UCastExpression -> parent.operand === node
            is UBinaryExpression -> {
                (parent.operator == UastBinaryOperator.AS
                        || parent.operator == UastBinaryOperator.AS_SAFE)
                        && parent.leftOperand === node
            }
            else -> false
        }
    }

    private fun getExpectedType(context: JavaContext, node: UCallExpression): PsiType? {
        val parent = node.uastParent ?: return null
        return when (parent) {
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN && parent.rightOperand === node) {
                    parent.leftOperand.getExpressionType()
                } else null
            }
            is UReturnExpression -> {
                parent.getParentOfType<UMethod>()?.returnType
            }
            is UCallExpression -> {
                val index = parent.valueArguments.indexOf(node)
                if (index >= 0) {
                    val called = parent.resolve() ?: return null
                    context.evaluator.getParameterType(called, index)
                } else null
            }
            else -> null
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, expectedType: PsiType) {
        val typeName = expectedType.presentableText
        context.report(
            ISSUE_FIND_VIEW_BY_ID_CAST,
            node,
            context.getLocation(node),
            "Add explicit cast to `$typeName` for `findViewById` result"
        )
    }

    companion object {
        @JvmField
        val ISSUE_FIND_VIEW_BY_ID_CAST: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}