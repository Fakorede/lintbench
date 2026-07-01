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
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isAssignment

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
        private const val ACTIVITY_CLASS = "android.app.Activity"
        private const val VIEW_GROUP_CLASS = "android.view.ViewGroup"
        private const val DIALOG_CLASS = "android.app.Dialog"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only care about calls on View, Activity, ViewGroup, Dialog, etc.
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        val isRelevant = evaluator.extendsClass(containingClass, ACTIVITY_CLASS, false) ||
                evaluator.extendsClass(containingClass, VIEW_CLASS, false) ||
                evaluator.extendsClass(containingClass, VIEW_GROUP_CLASS, false) ||
                evaluator.extendsClass(containingClass, DIALOG_CLASS, false) ||
                containingClass.qualifiedName == ACTIVITY_CLASS ||
                containingClass.qualifiedName == VIEW_CLASS

        if (!isRelevant) return

        // Check if this call is already inside an explicit cast expression
        val parent = skipParens(node.uastParent) ?: return

        // If the parent is already a cast expression, no need to warn
        if (parent is org.jetbrains.uast.UBinaryExpressionWithType) {
            // Already cast, no warning needed
            return
        }

        // Check if the result is assigned to a variable with a specific type
        // Look for patterns like:
        // TextView tv = findViewById(R.id.tv); -- Java
        // In Kotlin this is less of a concern but we still check

        // Check if the parent is a variable declaration
        val variable = node.getParentOfType<UVariable>(strict = true)
        if (variable != null) {
            val variableType = variable.type
            val variableTypeName = variableType.canonicalText

            // If the variable type is View or Object, no cast needed
            if (variableTypeName == VIEW_CLASS || variableTypeName == "java.lang.Object") {
                return
            }

            // If the variable type is a subtype of View, check if we need a cast
            val variablePsiClass = evaluator.findClass(variableTypeName)
            if (variablePsiClass != null && evaluator.extendsClass(variablePsiClass, VIEW_CLASS, false)) {
                // Check if there's already a cast
                val initializer = variable.uastInitializer
                if (initializer != null && !isCastExpression(initializer)) {
                    // Check if this is a Java file - Kotlin handles generics differently
                    val file = context.uastFile
                    if (file != null) {
                        val sourcePsi = node.sourcePsi
                        if (sourcePsi != null) {
                            val containingFile = sourcePsi.containingFile
                            if (containingFile != null && containingFile.name.endsWith(".java")) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Add explicit cast here; won't compile with Java 8 without it",
                                    createFix(context, node, variableTypeName)
                                )
                            }
                        }
                    }
                }
            }
            return
        }

        // Check assignment expressions like: mTextView = (TextView) findViewById(R.id.tv);
        val assignment = node.getParentOfType<UBinaryExpression>(strict = true)
        if (assignment != null && assignment.isAssignment()) {
            val leftType = assignment.leftOperand.getExpressionType() ?: return
            val leftTypeName = leftType.canonicalText

            if (leftTypeName == VIEW_CLASS || leftTypeName == "java.lang.Object") {
                return
            }

            val leftPsiClass = evaluator.findClass(leftTypeName)
            if (leftPsiClass != null && evaluator.extendsClass(leftPsiClass, VIEW_CLASS, false)) {
                val rightOperand = assignment.rightOperand
                if (!isCastExpression(rightOperand)) {
                    val sourcePsi = node.sourcePsi
                    if (sourcePsi != null) {
                        val containingFile = sourcePsi.containingFile
                        if (containingFile != null && containingFile.name.endsWith(".java")) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Add explicit cast here; won't compile with Java 8 without it",
                                createFix(context, node, leftTypeName)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun skipParens(element: UElement?): UElement? {
        var current = element
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    private fun isCastExpression(expression: UExpression): Boolean {
        return expression is org.jetbrains.uast.UBinaryExpressionWithType
    }

    private fun createFix(
        context: JavaContext,
        node: UCallExpression,
        castType: String
    ): com.android.tools.lint.detector.api.LintFix? {
        val simpleType = castType.substringAfterLast('.')
        val callText = node.sourcePsi?.text ?: return null
        return fix()
            .replace()
            .text(callText)
            .with("($simpleType) $callText")
            .autoFix()
            .build()
    }
}