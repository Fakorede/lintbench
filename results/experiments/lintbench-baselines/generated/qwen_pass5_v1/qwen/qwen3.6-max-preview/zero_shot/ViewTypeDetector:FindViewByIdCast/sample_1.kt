package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.getUastParentOfType

class ViewTypeDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.valueArgumentCount != 1) return

        val evaluator = context.evaluator
        val viewClass = evaluator.findClass("android.view.View") ?: return

        val containingClass = method.containingClass ?: return
        if (!evaluator.extendsClass(containingClass, viewClass, true) &&
            !evaluator.isClass(containingClass, "android.app.Activity") &&
            !evaluator.isClass(containingClass, "android.app.Dialog")) {
            return
        }

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        if (parent is UCastExpression) return

        val expectedType = getExpectedType(node, parent, context) ?: return

        if (evaluator.isClass(expectedType, "android.view.View")) return
        if (!evaluator.extendsClass(expectedType, viewClass, false)) return

        val sourceText = node.sourcePsi?.text ?: node.asSourceString()
        val fix = fix()
            .name("Add explicit cast")
            .replace()
            .range(context.getLocation(node))
            .with("(${expectedType.canonicalText}) $sourceText")
            .build()

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to `${expectedType.canonicalText}` for Java 8 compatibility",
            fix
        )
    }

    private fun getExpectedType(node: UCallExpression, parent: UElement?, context: JavaContext): PsiType? {
        val evaluator = context.evaluator
        return when (parent) {
            is ULocalVariable -> parent.type
            is UField -> parent.type
            is UAssignmentExpression -> evaluator.getType(parent.leftOperand)
            is UReturnExpression -> {
                val method = parent.getUastParentOfType<UMethod>()
                method?.returnType
            }
            is UCallExpression -> {
                val index = parent.valueArguments.indexOf(node)
                if (index >= 0) {
                    val resolved = parent.resolve()
                    resolved?.parameterList?.parameters?.getOrNull(index)?.type
                } else null
            }
            else -> null
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
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