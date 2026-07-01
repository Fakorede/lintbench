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
import org.jetbrains.uast.ULocalVariable
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

        // Check if the containing class is a known class or subclass
        if (!isKnownFindViewByIdClass(className)) {
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
            }
            // If we can't find the class, still proceed - it might be a valid case
        }

        // Now check if the call result is used in a context that requires a cast
        // but doesn't have one - look at the parent expression
        val parent = node.uastParent ?: return

        // Case 1: Direct variable declaration
        // e.g., TextView tv = findViewById(R.id.text);
        if (parent is UVariable) {
            val declaredType = parent.type
            checkTypeAndReport(context, node, declaredType)
            return
        }

        // Case 2: Assignment expression without cast
        // e.g., tv = findViewById(R.id.text);
        if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
            val leftType = parent.leftOperand.getExpressionType() ?: return
            checkTypeAndReport(context, node, leftType)
            return
        }

        // Case 3: Return statement
        // e.g., return findViewById(R.id.text);
        if (parent is UReturnExpression) {
            val containingMethod = node.getParentOfType<UMethod>()
            val methodReturnType = containingMethod?.returnType ?: return
            checkTypeAndReport(context, node, methodReturnType)
            return
        }

        // Case 4: Parenthesized expression wrapping - check grandparent
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent ?: return
            if (grandParent is UVariable) {
                val declaredType = grandParent.type
                checkTypeAndReport(context, node, declaredType)
            } else if (grandParent is UBinaryExpression && grandParent.operator == UastBinaryOperator.ASSIGN) {
                val leftType = grandParent.leftOperand.getExpressionType() ?: return
                checkTypeAndReport(context, node, leftType)
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

        // Check if it's a View subtype or is a View-related type
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
        // If we can't resolve the class, check if it looks like a View subclass
        // by checking common patterns - assume it might be a View subclass
        // For unknown classes, we can't be sure, so return false to avoid false positives
        return false
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