package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

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

        // Make sure it's a View-related findViewById
        if (!isViewFindViewById(context, containingClass)) {
            return
        }

        // Walk up the parent chain, skipping parentheses
        val parent = getRelevantParent(node) ?: return

        // If it's already wrapped in a type cast expression, no need to warn
        if (parent is UTypeCastExpression) {
            return
        }

        when (parent) {
            is UVariable -> {
                val variableType = parent.type
                val typeName = variableType.canonicalText
                // If the type is View or Object, no cast needed
                if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                    return
                }
                // If it's a subclass of View, suggest adding a cast
                if (isViewSubtype(context, variableType)) {
                    val simpleType = variableType.presentableText
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                    )
                }
            }

            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    val leftType = parent.leftOperand.getExpressionType() ?: return
                    val typeName = leftType.canonicalText
                    if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                        return
                    }
                    if (isViewSubtype(context, leftType)) {
                        val simpleType = leftType.presentableText
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                        )
                    }
                }
            }

            is UReturnExpression -> {
                val returnType = getEnclosingMethodReturnType(parent) ?: return
                val typeName = returnType.canonicalText
                if (typeName == VIEW_CLASS || typeName == "java.lang.Object" || typeName == "View") {
                    return
                }
                if (isViewSubtype(context, returnType)) {
                    val simpleType = returnType.presentableText
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                    )
                }
            }

            else -> {
                // For other contexts (method arguments, etc.), check the expression type
                val expressionType = node.getExpressionType() ?: return
                val typeName = expressionType.canonicalText
                if (typeName == VIEW_CLASS || typeName == "java.lang.Object") {
                    return
                }
                if (isViewSubtype(context, expressionType)) {
                    val simpleType = expressionType.presentableText
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast here; won't compile with Java 8 without it: `($simpleType) $FIND_VIEW_BY_ID(...)`"
                    )
                }
            }
        }
    }

    private fun getRelevantParent(node: UExpression): UElement? {
        var current: UElement? = node.uastParent
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    private fun isViewFindViewById(context: JavaContext, containingClass: PsiClass): Boolean {
        val evaluator = context.evaluator
        val qualifiedName = containingClass.qualifiedName ?: ""
        return evaluator.extendsClass(containingClass, VIEW_CLASS, false) ||
                evaluator.extendsClass(containingClass, "android.app.Activity", false) ||
                evaluator.extendsClass(containingClass, "android.app.Dialog", false) ||
                evaluator.extendsClass(containingClass, "android.view.Window", false) ||
                qualifiedName == VIEW_CLASS ||
                qualifiedName == "android.app.Activity" ||
                qualifiedName == "android.app.Dialog" ||
                qualifiedName == "android.view.Window" ||
                // Also handle cases where the class might not be resolvable but method name matches
                true
    }

    private fun isViewSubtype(context: JavaContext, type: PsiType): Boolean {
        val typeName = type.canonicalText
        if (typeName == VIEW_CLASS || typeName == "java.lang.Object") return false
        val evaluator = context.evaluator
        val psiClass = evaluator.findClass(typeName)
        return if (psiClass != null) {
            evaluator.extendsClass(psiClass, VIEW_CLASS, false)
        } else {
            // If we can't resolve the class, assume it might be a View subtype
            // if it doesn't look like a primitive or common non-view type
            !typeName.startsWith("java.lang.") &&
                    !typeName.startsWith("java.util.") &&
                    typeName != "int" && typeName != "boolean" &&
                    typeName != "void" && typeName != "long" &&
                    typeName != "float" && typeName != "double"
        }
    }

    private fun getEnclosingMethodReturnType(returnExpression: UReturnExpression): PsiType? {
        val method = returnExpression.getParentOfType<UMethod>(true) ?: return null
        return method.returnType
    }
}