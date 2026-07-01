package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "findViewById") return

                val method = node.resolve() ?: return
                if (!isTargetFindViewById(method)) return

                if (node.uastParent is UCastExpression) return

                val evaluator = context.evaluator
                val expectedType = node.getExpectedType()

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
        if (method.parameterList.parametersCount != 1) return false
        val qualifiedName = method.containingClass?.qualifiedName ?: return false
        return qualifiedName == "android.app.Activity" ||
                qualifiedName == "android.view.View" ||
                qualifiedName == "android.app.Dialog" ||
                qualifiedName == "androidx.fragment.app.Fragment" ||
                qualifiedName == "android.app.Fragment"
    }

    private fun needsExplicitCast(expectedType: PsiType?, evaluator: JavaEvaluator): Boolean {
        if (expectedType == null) return false
        val typeClass = evaluator.getTypeClass(expectedType) ?: return false
        val qualifiedName = typeClass.qualifiedName ?: return false
        if (qualifiedName == "android.view.View") return false
        return evaluator.extendsClass(typeClass, "android.view.View", false)
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