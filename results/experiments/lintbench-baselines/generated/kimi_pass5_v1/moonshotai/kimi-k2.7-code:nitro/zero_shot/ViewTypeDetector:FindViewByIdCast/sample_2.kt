package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes() =
        listOf(UBinaryExpression::class.java, UVariable::class.java, UReturnExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator != UastBinaryOperator.ASSIGN) return
                val rhs = node.rightOperand
                if (isFindViewByIdCall(rhs)) {
                    val expectedType = node.leftOperand.getExpressionType()
                    checkFindViewByIdCast(context, rhs as UCallExpression, expectedType)
                }
            }

            override fun visitVariable(node: UVariable) {
                val initializer = node.uastInitializer ?: return
                if (isFindViewByIdCall(initializer)) {
                    checkFindViewByIdCast(context, initializer as UCallExpression, node.type)
                }
            }

            override fun visitReturnExpression(node: UReturnExpression) {
                val returnValue = node.returnExpression ?: return
                if (isFindViewByIdCall(returnValue)) {
                    val method = node.getParentOfType(UMethod::class.java, true)
                    val expectedType = method?.returnType
                    checkFindViewByIdCast(context, returnValue as UCallExpression, expectedType)
                }
            }
        }
    }

    private fun isFindViewByIdCall(expression: UExpression?): Boolean {
        val call = expression as? UCallExpression ?: return false
        if (call.methodName != "findViewById") return false
        val method = call.resolve() ?: return false
        val containingClass = method.containingClass ?: return false
        val className = containingClass.qualifiedName ?: return false
        return className == "android.view.View" ||
                className == "android.app.Activity" ||
                className == "android.support.v4.app.FragmentActivity" ||
                className == "androidx.fragment.app.FragmentActivity" ||
                InheritanceUtil.isInheritor(containingClass, "android.view.View") ||
                InheritanceUtil.isInheritor(containingClass, "android.app.Activity") ||
                InheritanceUtil.isInheritor(containingClass, "android.support.v4.app.FragmentActivity") ||
                InheritanceUtil.isInheritor(containingClass, "androidx.fragment.app.FragmentActivity")
    }

    private fun checkFindViewByIdCast(
        context: JavaContext,
        call: UCallExpression,
        expectedType: PsiType?
    ) {
        if (expectedType == null) return
        if (!context.file.name.endsWith(".java")) return

        var parent = call.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UTypeCastExpression) return

        if (expectedType.canonicalText == "android.view.View") return

        if (InheritanceUtil.isInheritor(expectedType, "android.view.View")) {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Add explicit cast: `findViewById` should be cast to `${expectedType.presentableText}` " +
                        "to ensure the code compiles when the language level is updated to 1.8."
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which
                means that most of the time you can leave out explicit casts and just assign
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause
                code to not compile without explicit casts. This lint check looks for these
                scenarios and suggests casts to be added now such that the code will continue
                to compile if the language level is updated to 1.8.
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