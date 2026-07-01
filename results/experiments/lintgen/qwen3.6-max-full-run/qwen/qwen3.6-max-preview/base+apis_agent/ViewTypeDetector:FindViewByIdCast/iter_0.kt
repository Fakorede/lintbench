package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "findViewById") return

                val method = node.resolve() ?: return
                if (!isTargetFindViewById(method)) return

                // If the call is already explicitly cast, skip it
                if (node.uastParent is UCastExpression) return

                val evaluator = context.evaluator
                val expectedType = evaluator.getExpectedType(node)

                if (needsExplicitCast(expectedType, evaluator)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast to avoid Java 8 type inference issues with generic findViewById"
                    )
                }
            }
        }
    }

    private fun isTargetFindViewById(method: PsiMethod): Boolean {
        val qualifiedName = method.containingClass?.qualifiedName ?: return false
        return (qualifiedName == "android.app.Activity" ||
                qualifiedName == "android.view.View" ||
                qualifiedName == "androidx.core.view.ViewCompat") &&
                method.parameterList.parametersCount == 1
    }

    private fun needsExplicitCast(expectedType: PsiType?, evaluator: JavaEvaluator): Boolean {
        if (expectedType == null) return true
        val canonical = expectedType.canonicalText
        if (canonical == "android.view.View" || canonical == "java.lang.Object") return true

        val typeClass = evaluator.getTypeClass(expectedType) ?: return true
        return !evaluator.extendsClass(typeClass, "android.view.View", false)
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