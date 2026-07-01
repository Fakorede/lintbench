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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
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
        val containingFile = node.sourcePsi?.containingFile ?: return
        if (containingFile.name.endsWith(".kt")) {
            return
        }

        // Check that this is actually the Android View.findViewById or Activity.findViewById
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: return

        val evaluator = context.evaluator

        // Verify the method is from a View-related class or known class
        val psiClass = evaluator.findClass(className)
        if (psiClass != null) {
            val isViewSubclass = evaluator.extendsClass(psiClass, VIEW_CLASS, false)
            val isActivitySubclass = evaluator.extendsClass(psiClass, "android.app.Activity", false)
            val isDialogSubclass = evaluator.extendsClass(psiClass, "android.app.Dialog", false)
            val isFragmentSubclass = evaluator.extendsClass(psiClass, "android.app.Fragment", false) ||
                    evaluator.extendsClass(psiClass, "androidx.fragment.app.Fragment", false) ||
                    evaluator.extendsClass(psiClass, "android.support.v4.app.Fragment", false)
            if (!isViewSubclass && !isActivitySubclass && !isDialogSubclass && !isFragmentSubclass) {
                return
            }
        } else if (!isKnownFindViewByIdClass(className)) {
            return
        }

        // Walk up the parent chain to find the context of this call
        checkParent(context, node, node.uastParent)
    }

    private fun checkParent(context: JavaContext, node: UCallExpression, parent: UElement?) {
        parent ?: return

        when (parent) {
            is UVariable -> {
                val declaredType = parent.type
                checkTypeAndReport(context, node, declaredType)
            }
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    val leftType = parent.leftOperand.getExpressionType() ?: return
                    checkTypeAndReport(context, node, leftType)
                }
            }
            is UReturnExpression -> {
                val containingMethod = node.getParentOfType<UMethod>()
                val methodReturnType = containingMethod?.returnType ?: return
                checkTypeAndReport(context, node, methodReturnType)
            }
            is UParenthesizedExpression -> {
                // Skip parentheses and check grandparent
                checkParent(context, node, parent.uastParent)
            }
            is UExpression -> {
                // For other expression contexts, check if there's an implicit type context
                val expressionType = parent.getExpressionType()
                if (expressionType != null) {
                    checkTypeAndReport(context, node, expressionType)
                }
            }
        }
    }

    private fun checkTypeAndReport(context: JavaContext, node: UCallExpression, type: PsiType) {
        val typeName = type.canonicalText
        if (typeName == VIEW_CLASS ||
            typeName == "java.lang.Object" ||
            typeName == "void"
        ) {
            return
        }

        // Check if it's a View subtype
        if (isViewSubtype(context, typeName)) {
            reportMissingCast(context, node, typeName)
        }
    }

    private fun isKnownFindViewByIdClass(className: String): Boolean {
        return className == "android.view.View" ||
                className == "android.app.Activity" ||
                className == "android.app.Dialog" ||
                className == "android.app.Fragment" ||
                className == "androidx.fragment.app.Fragment" ||
                className == "android.support.v4.app.Fragment"
    }

    private fun isViewSubtype(context: JavaContext, typeName: String): Boolean {
        if (typeName == VIEW_CLASS) return false
        val evaluator = context.evaluator
        val psiClass = evaluator.findClass(typeName)
        if (psiClass != null) {
            return evaluator.extendsClass(psiClass, VIEW_CLASS, false)
        }
        // If we can't resolve the class, use heuristics based on common View subclass naming
        // and package patterns to avoid false negatives
        return typeName.endsWith("View") ||
                typeName.endsWith("Button") ||
                typeName.endsWith("TextView") ||
                typeName.endsWith("EditText") ||
                typeName.endsWith("Layout") ||
                typeName.endsWith("ImageView") ||
                typeName.endsWith("ListView") ||
                typeName.endsWith("RecyclerView") ||
                typeName.contains("android.widget.") ||
                typeName.contains("android.view.")
    }

    private fun reportMissingCast(context: JavaContext, node: UCallExpression, castType: String) {
        val simpleName = castType.substringAfterLast('.')
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java language level 1.8 " +
                    "without it: `($simpleName) ${node.asSourceString()}`"
        )
    }
}