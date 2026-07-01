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
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
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
        private const val VIEW_CLASS = "android.view.View"
        private const val OBJECT_CLASS = "java.lang.Object"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only process Java files - Kotlin handles generics differently
        val psiFile = context.psiFile ?: return
        if (!psiFile.name.endsWith(".java")) return

        // Walk up through parenthesized expressions to find the real parent
        val parent = skipParenthesizedExprUp(node.uastParent) ?: return

        // If already explicitly cast, no need to warn
        if (parent is UBinaryExpressionWithType) {
            return
        }

        // Find the target type by examining the context of this call
        val targetType = findTargetType(context, node, parent) ?: return

        if (isSpecificViewType(targetType)) {
            val simpleName = targetType.substringAfterLast('.')
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; won't compile with Java 8 without it: `($simpleName) ${node.asSourceString()}`"
            )
        }
    }

    private fun findTargetType(context: JavaContext, node: UCallExpression, parent: UElement): String? {
        // Case 1: Direct assignment to a variable
        // e.g., TextView tv = findViewById(R.id.text);
        if (parent is UVariable) {
            return parent.type.canonicalText
        }

        // Case 2: The call is part of a qualified expression chain
        // Walk up through qualified references and parentheses
        var current: UElement = node
        var p: UElement? = parent

        while (p is UQualifiedReferenceExpression || p is UParenthesizedExpression) {
            current = p
            p = p.uastParent
        }

        if (p == null) return null

        // Check if the result ends up in a variable assignment
        if (p is UVariable) {
            return p.type.canonicalText
        }

        // Case 3: Return statement
        if (p is UReturnExpression) {
            val containingMethod = node.getParentOfType<UMethod>(strict = true) ?: return null
            val returnType = containingMethod.returnType ?: return null
            return returnType.canonicalText
        }

        // Case 4: Check if the expression type is inferred to something specific
        // This handles cases where the generic return type is inferred
        val expressionType = node.getExpressionType()
        if (expressionType != null) {
            val typeName = expressionType.canonicalText
            if (typeName != VIEW_CLASS && typeName != OBJECT_CLASS && isSpecificViewType(typeName)) {
                return typeName
            }
        }

        return null
    }

    private fun isSpecificViewType(typeName: String): Boolean {
        return typeName != VIEW_CLASS &&
                typeName != OBJECT_CLASS &&
                typeName != "null" &&
                !typeName.startsWith("?") &&
                typeName.isNotEmpty() &&
                !typeName.contains("<") // avoid generic types
    }
}