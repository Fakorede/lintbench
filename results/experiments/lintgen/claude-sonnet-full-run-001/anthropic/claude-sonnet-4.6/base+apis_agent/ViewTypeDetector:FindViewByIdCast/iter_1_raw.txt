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
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprUp

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
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only process Java files - Kotlin handles generics differently
        val file = context.psiFile ?: return
        if (!file.name.endsWith(".java")) return

        // Check if this call is already wrapped in an explicit cast
        val parent = skipParenthesizedExprUp(node.uastParent) ?: return

        if (parent is UBinaryExpressionWithType) {
            // Already explicitly cast
            return
        }

        // Check if assigned to a local variable
        if (parent is UVariable || parent is ULocalVariable) {
            val variable = parent as UVariable
            val variableType = variable.type
            val typeName = variableType.canonicalText
            // If the variable type is View or Object, no cast needed
            if (typeName == "android.view.View" || typeName == "java.lang.Object") {
                return
            }
            // If it's a specific View subtype, we need a cast
            reportIssue(context, node, typeName)
            return
        }

        // Handle case where it's part of a qualified expression (e.g., assigned via field)
        // Walk up through qualified references
        var current: UElement = node
        var p = parent
        while (p is UQualifiedReferenceExpression || p is UParenthesizedExpression) {
            current = p
            p = p.uastParent ?: break
        }

        // Check if the result is used in a context that requires a specific type
        // by looking at the expression type vs the inferred type
        val expressionType = node.getExpressionType() ?: return
        val returnTypeName = expressionType.canonicalText

        // If the return type is already View (not generic), check context
        if (returnTypeName == "android.view.View") {
            // Look for assignment context
            checkAssignmentContext(context, node, p ?: return)
        }
    }

    private fun checkAssignmentContext(context: JavaContext, node: UCallExpression, parent: UElement) {
        // Check if used in a return statement where return type is more specific
        if (parent is UReturnExpression) {
            val method = node.getParentOfType<org.jetbrains.uast.UMethod>(strict = true) ?: return
            val returnType = method.returnType ?: return
            val returnTypeName = returnType.canonicalText
            if (returnTypeName != "android.view.View" && returnTypeName != "java.lang.Object") {
                reportIssue(context, node, returnTypeName)
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression, targetType: String) {
        val simpleName = targetType.substringAfterLast('.')
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java 8 without it: `($simpleName) ${node.asSourceString()}`"
        )
    }
}