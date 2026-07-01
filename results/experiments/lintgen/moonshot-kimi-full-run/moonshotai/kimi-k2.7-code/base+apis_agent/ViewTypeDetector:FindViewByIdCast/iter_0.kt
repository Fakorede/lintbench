package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which
                means that most of the time you can leave out explicit casts and just assign
                the result of the `findViewById` call to variables of specific view classes.
                However, due to language changes between Java 7 and 8, this change may cause
                code to not compile without explicit casts. This check flags those scenarios
                and suggests adding an explicit cast so the code continues to compile when the
                language level is updated to 1.8.
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

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaElementVisitor
    ) {
        if (context.isKotlin(node)) {
            return
        }

        val method = node.resolve() ?: return
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        if (!evaluator.extendsClass(containingClass, "android.view.View", false) &&
            !evaluator.extendsClass(containingClass, "android.app.Activity", false)
        ) {
            return
        }

        if (node.typeArguments.isNotEmpty()) return
        if (isAlreadyCast(node)) return

        val expectedType = getExpectedType(node) ?: return
        if (PsiType.VOID == expectedType) return

        val expectedClass = (expectedType as? PsiClassType)?.resolve() ?: return
        if (expectedType.canonicalText == "android.view.View") return
        if (!evaluator.extendsClass(expectedClass, "android.view.View", false)) return

        val cast = expectedType.presentableText
        val original = node.asSourceString()
        val message = "Add explicit cast to $cast"
        val fix = LintFix.create()
            .name("Cast to $cast")
            .replace()
            .range(context.getLocation(node))
            .with("($cast) $original")
            .build()

        context.report(ISSUE, node, context.getLocation(node), message, fix)
    }

    private fun isAlreadyCast(node: UCallExpression): Boolean {
        var current: UElement = node
        while (true) {
            when (val parent = current.uastParent ?: return false) {
                is UTypeCastExpression -> return true
                is UParenthesizedExpression -> current = parent
                else -> return false
            }
        }
    }

    private fun getExpectedType(node: UExpression): PsiType? {
        var current: UElement = node
        while (true) {
            val parent = current.uastParent ?: return null
            when {
                parent is UVariable -> return parent.type
                parent is UBinaryExpression &&
                    parent.operator == UastBinaryOperator.ASSIGN &&
                    parent.rightOperand === current ->
                    return parent.leftOperand.getExpressionType()
                parent is UTypeCastExpression -> return null
            }
            current = parent
        }
    }
}