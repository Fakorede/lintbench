package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.util.isMethodCall

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, \
                which means that most of the time you can leave out explicit casts and \
                just assign the result of the `findViewById` call to variables of specific \
                view classes.

                However, due to language changes between Java 7 and 8, this change may \
                cause code to not compile without explicit casts. This lint check looks \
                for these scenarios and suggests casts to be added now such that the \
                code will continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val REQUIRE_VIEW_BY_ID = "requireViewById"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID, REQUIRE_VIEW_BY_ID)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Only care about Java files (not Kotlin)
        val file = context.uastFile ?: return
        val psiFile = file.sourcePsi ?: return
        if (psiFile.language.id == "kotlin" || psiFile.name.endsWith(".kt")) {
            return
        }

        // Check if this call is being used in a context that requires an explicit cast
        // We need to detect situations where generics inference might fail under Java 8
        val parent = node.uastParent ?: return

        // Look for patterns like:
        // someMethod(findViewById(R.id.foo)) where someMethod is overloaded
        // or chained method calls like findViewById(R.id.foo).setText(...)
        // or variable assignments without cast

        // Check if the result is passed directly to a method call (argument position)
        // This is the main problematic case: overloaded methods where type inference fails
        if (isArgumentToMethodCall(node, parent)) {
            val containingCall = getContainingCall(node, parent) ?: return
            if (hasOverloadedVersions(context, node, containingCall)) {
                reportIssue(context, node)
            }
        }
    }

    private fun isArgumentToMethodCall(node: UCallExpression, parent: UElement): Boolean {
        // Check if the node is an argument in another method call
        if (parent is UCallExpression) {
            return parent.valueArguments.any { it === node || unwrap(it) === node }
        }
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent ?: return false
            return isArgumentToMethodCall(node, grandParent)
        }
        return false
    }

    private fun unwrap(expr: UExpression): UExpression {
        return if (expr is UParenthesizedExpression) unwrap(expr.expression) else expr
    }

    private fun getContainingCall(node: UCallExpression, parent: UElement): UCallExpression? {
        if (parent is UCallExpression) {
            return parent
        }
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent ?: return null
            return getContainingCall(node, grandParent)
        }
        return null
    }

    private fun hasOverloadedVersions(
        context: JavaContext,
        node: UCallExpression,
        containingCall: UCallExpression
    ): Boolean {
        val evaluator = context.evaluator
        val methodName = containingCall.methodName ?: return false
        val receiverType = containingCall.receiverType

        // Get all methods with the same name in the receiver class
        val psiClass = if (receiverType != null) {
            evaluator.getTypeClass(receiverType)
        } else {
            // Try to get the containing class
            null
        } ?: return false

        val methods = psiClass.findMethodsByName(methodName, true)
        return methods.size > 1
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java 8 language level"
        )
    }
}