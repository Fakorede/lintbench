package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), Detector.UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.parameterList.parametersCount != 1) return

        var parent: UElement? = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        if (parent?.sourcePsi is PsiTypeCastExpression) return

        val expectedType = when (parent) {
            is ULocalVariable -> parent.type
            is UField -> parent.type
            is UReturnExpression -> parent.getParentOfType(UMethod::class.java, true)?.returnType
            is UCallExpression -> {
                val argIndex = parent.valueArguments.indexOfFirst { arg ->
                    arg == node || (arg is UParenthesizedExpression && arg.expression == node)
                }
                if (argIndex != -1) {
                    val resolved = parent.resolve()
                    resolved?.parameterList?.parameters?.getOrNull(argIndex)?.type
                } else null
            }
            else -> null
        }

        val qualifiedName = (expectedType as? PsiClassType)?.resolve()?.qualifiedName
        if (qualifiedName == "android.view.View" || qualifiedName == "java.lang.Object") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to `findViewById` to ensure compatibility with Java 8 type inference"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "FindViewByIdCast",
            "Add Explicit Cast",
            "In Android O, the `findViewById` signature switched to using generics, which " +
                "means that most of the time you can leave out explicit casts and just assign " +
                "the result of the `findViewById` call to variables of specific view classes.\n\n" +
                "However, due to language changes between Java 7 and 8, this change may cause " +
                "code to not compile without explicit casts. This lint check looks for these " +
                "scenarios and suggests casts to be added now such that the code will " +
                "continue to compile if the language level is updated to 1.8.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}