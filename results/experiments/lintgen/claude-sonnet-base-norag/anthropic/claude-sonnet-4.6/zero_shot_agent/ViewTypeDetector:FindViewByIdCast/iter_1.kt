package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

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
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        // Check if the method is a findViewById method on a View or Activity-like class
        if (!evaluator.extendsClass(containingClass, VIEW_CLASS, false) &&
            !evaluator.extendsClass(containingClass, "android.app.Activity", false) &&
            !evaluator.extendsClass(containingClass, "android.app.Dialog", false) &&
            !evaluator.extendsClass(containingClass, "androidx.fragment.app.Fragment", false) &&
            !evaluator.extendsClass(containingClass, "android.app.Fragment", false) &&
            containingClass.qualifiedName != "android.view.View" &&
            containingClass.qualifiedName != "android.app.Activity"
        ) {
            return
        }

        // Check if the result is already cast by examining the parent node class name
        val parent = node.uastParent ?: return

        // If the parent is a cast expression, no need to warn
        // We check by class name to avoid importing UTypeCastExpression which may not be available
        val parentClassName = parent.javaClass.simpleName
        if (parentClassName == "UTypeCastExpressionImpl" || parentClassName.contains("TypeCast")) {
            return
        }
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent
            val grandParentClassName = grandParent?.javaClass?.simpleName ?: ""
            if (grandParentClassName == "UTypeCastExpressionImpl" || grandParentClassName.contains("TypeCast")) {
                return
            }
        }

        // Case 1: Variable declaration with initializer
        val variable = node.getParentOfType<UVariable>(true)
        if (variable != null) {
            val variableType = variable.type
            if (isViewSubtype(context, variableType)) {
                val typeName = getSimpleTypeName(variableType)
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java language level 1.8 " +
                            "without it: `($typeName) ${node.asSourceString()}`"
                )
            }
            return
        }

        // Case 2: Return statement
        val returnExpr = node.getParentOfType<UReturnExpression>(true)
        if (returnExpr != null) {
            val containingMethod = returnExpr.getParentOfType<UMethod>(true)
            if (containingMethod != null) {
                val returnType = containingMethod.returnType
                if (returnType != null && isViewSubtype(context, returnType)) {
                    val typeName = getSimpleTypeName(returnType)
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast here; won't compile with Java language level 1.8 " +
                                "without it: `($typeName) ${node.asSourceString()}`"
                    )
                }
            }
            return
        }

        // Case 3: Assignment expression
        val binaryExpr = node.getParentOfType<UBinaryExpression>(true)
        if (binaryExpr != null && binaryExpr.operator == UastBinaryOperator.ASSIGN) {
            val leftType = binaryExpr.leftOperand.getExpressionType()
            if (leftType != null && isViewSubtype(context, leftType)) {
                val typeName = getSimpleTypeName(leftType)
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java language level 1.8 " +
                            "without it: `($typeName) ${node.asSourceString()}`"
                )
            }
            return
        }
    }

    private fun isViewSubtype(context: JavaContext, type: PsiType): Boolean {
        if (type !is PsiClassType) return false
        val cls = type.resolve() ?: return false
        val qualifiedName = cls.qualifiedName ?: return false
        // It should be a subtype of View but not View itself
        if (qualifiedName == VIEW_CLASS) return false
        return context.evaluator.extendsClass(cls, VIEW_CLASS, false)
    }

    private fun getSimpleTypeName(type: PsiType): String {
        if (type is PsiClassType) {
            val cls = type.resolve()
            if (cls != null) {
                return cls.name ?: type.presentableText
            }
        }
        return type.presentableText
    }
}