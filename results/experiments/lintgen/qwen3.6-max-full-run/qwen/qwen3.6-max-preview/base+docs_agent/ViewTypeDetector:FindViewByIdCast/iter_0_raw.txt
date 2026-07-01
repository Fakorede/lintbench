package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
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

    override fun getApplicableMethodNames(): List<String>? = listOf("findViewById", "requireViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name != "findViewById" && method.name != "requireViewById") return

        val params = method.parameterList.parameters
        if (params.size != 1 || params[0].type != PsiType.INT) return

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        if (parent is UCastExpression) return

        val needsCast = when (parent) {
            is UCallExpression -> parent.receiver == node || parent.valueArguments.contains(node)
            is UQualifiedReferenceExpression -> parent.receiver == node
            else -> false
        }

        if (needsCast) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Add explicit cast here to ensure Java 8 compatibility"
            )
        }
    }
}