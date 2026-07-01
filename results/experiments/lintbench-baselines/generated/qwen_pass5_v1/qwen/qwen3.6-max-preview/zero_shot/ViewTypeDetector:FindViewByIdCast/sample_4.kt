package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import java.util.EnumSet

class ViewTypeDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val params = method.parameterList.parameters
        if (params.size != 1 || params[0].type != PsiType.INT) return

        if (node.uastParent is UCastExpression) return

        val parent = node.uastParent
        val isSafeContext = when (parent) {
            is UBinaryExpression -> parent.operator == UastBinaryOperator.ASSIGN
            is ULocalVariable, is UField -> true
            else -> false
        }

        if (isSafeContext) return

        val sourceText = node.sourcePsi?.text ?: return
        val fix = LintFix.create()
            .name("Add explicit View cast")
            .replace()
            .text(sourceText)
            .with("(View) $sourceText")
            .autoFix()
            .build()

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to `View` for Java 8 compatibility",
            fix
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}