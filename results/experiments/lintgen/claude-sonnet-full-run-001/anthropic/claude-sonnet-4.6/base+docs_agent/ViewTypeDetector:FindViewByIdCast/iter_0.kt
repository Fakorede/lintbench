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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.UField
import org.jetbrains.uast.UVariable

class ViewTypeDetector : Detector(), SourceCodeScanner {

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
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only care about Java files - Kotlin handles this differently
        val file = context.psiFile ?: return
        if (file.name.endsWith(".kt")) {
            return
        }

        // Check that this is actually the Android View.findViewById or Activity.findViewById
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: return

        // Make sure it's a View-related findViewById
        if (!isViewFindViewById(context, containingClass)) {
            return
        }

        // Check if the result is already cast
        val parent = skipParentheses(node.uastParent) ?: return

        // If it's already wrapped in a cast expression, no need to warn
        if (parent is UTypeCastExpression) {
            return
        }

        // Check if assigned to a variable with a specific type
        if (parent is UVariable) {
            val variableType = parent.type
            val typeName = variableType.canonicalText
            // If the type is View or Object, no cast needed
            if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                return
            }
            // If it's a subclass of View, suggest adding a cast
            if (isViewSubtype(context, typeName)) {
                val simpleType = variableType.presentableText
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                )
            }
            return
        }

        // Check if used in an assignment
        if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
            val leftType = parent.leftOperand.getExpressionType() ?: return
            val typeName = leftType.canonicalText
            if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                return
            }
            if (isViewSubtype(context, typeName)) {
                val simpleType = leftType.presentableText
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                )
            }
            return
        }

        // Check if used in a return statement
        if (parent is UReturnExpression) {
            val returnType = getReturnType(context, parent) ?: return
            val typeName = returnType
            if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                return
            }
            if (isViewSubtype(context, typeName)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java 8 without it: `($typeName) $FIND_VIEW_BY_ID(...)`"
                )
            }
        }
    }

    private fun skipParentheses(element: UElement?): UElement? {
        var current = element
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    private fun isViewFindViewById(context: JavaContext, containingClass: com.intellij.psi.PsiClass): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(containingClass, VIEW_CLASS, false) ||
                evaluator.extendsClass(containingClass, "android.app.Activity", false) ||
                evaluator.extendsClass(containingClass, "android.app.Dialog", false) ||
                evaluator.implementsInterface(containingClass, "android.view.Window", false) ||
                containingClass.qualifiedName == VIEW_CLASS ||
                containingClass.qualifiedName == "android.app.Activity" ||
                containingClass.qualifiedName == "android.app.Dialog"
    }

    private fun isViewSubtype(context: JavaContext, typeName: String): Boolean {
        if (typeName == VIEW_CLASS) return false
        val evaluator = context.evaluator
        val psiClass = evaluator.findClass(typeName) ?: return false
        return evaluator.extendsClass(psiClass, VIEW_CLASS, false)
    }

    private fun getReturnType(context: JavaContext, returnExpression: UReturnExpression): String? {
        // Walk up to find the containing method
        var element: UElement? = returnExpression.uastParent
        while (element != null) {
            if (element is org.jetbrains.uast.UMethod) {
                val returnType = element.returnType ?: return null
                return returnType.canonicalText
            }
            element = element.uastParent
        }
        return null
    }
}