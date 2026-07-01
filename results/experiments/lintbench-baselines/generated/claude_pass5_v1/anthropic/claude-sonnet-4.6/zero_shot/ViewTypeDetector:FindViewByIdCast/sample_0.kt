package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isMethodCall

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
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
        private const val ACTIVITY_CLASS = "android.app.Activity"
        private const val VIEW_GROUP_CLASS = "android.view.View"
        private const val DIALOG_CLASS = "android.app.Dialog"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) return

                val methodName = node.methodName ?: return
                if (methodName != FIND_VIEW_BY_ID) return

                val method = node.resolve() ?: return
                val containingClass = method.containingClass ?: return

                // Check if the method is from a View, Activity, Dialog, or similar class
                val evaluator = context.evaluator
                if (!evaluator.extendsClass(containingClass, ACTIVITY_CLASS, false) &&
                    !evaluator.extendsClass(containingClass, VIEW_CLASS, false) &&
                    !evaluator.extendsClass(containingClass, DIALOG_CLASS, false) &&
                    !evaluator.implementsInterface(containingClass, "android.view.Window", false)
                ) {
                    return
                }

                // Check the return type of the method
                val returnType = method.returnType ?: return
                val returnTypeName = returnType.canonicalText

                // If the return type is already generic (T), we're in the new API
                // We need to check if the assignment context requires a cast
                // Look for cases where the result is assigned to a typed variable
                // without an explicit cast

                // Check if this call is the direct child of a variable declaration
                // (i.e., the result is assigned without a cast)
                val parent = node.uastParent ?: return

                // We're looking for: SomeView v = findViewById(R.id.something);
                // without a cast, in a context where Java 8 inference would handle it
                // but Java 7 would not

                // Check if return type is View (non-generic old API)
                if (returnTypeName != VIEW_CLASS && !returnTypeName.startsWith("android.view.View")) {
                    return
                }

                // Look at the assignment context
                val assignedType = getAssignedType(node) ?: return

                // If assigned type is View or Object, no cast needed
                val assignedTypeName = assignedType.canonicalText
                if (assignedTypeName == VIEW_CLASS ||
                    assignedTypeName == "java.lang.Object" ||
                    assignedTypeName == "android.view.View"
                ) {
                    return
                }

                // Check if the assigned type is a subtype of View
                if (assignedType is PsiClassType) {
                    val assignedClass = assignedType.resolve() ?: return
                    if (!evaluator.extendsClass(assignedClass, VIEW_CLASS, false)) {
                        return
                    }

                    // This is a potential issue: assigning to a specific View subtype
                    // without an explicit cast
                    val message = "Add explicit cast here; won't compile with Java 8 " +
                            "without it: `($assignedTypeName) findViewById(...)`"

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }
    }

    private fun getAssignedType(node: UCallExpression): PsiType? {
        val parent = node.uastParent ?: return null

        // Case 1: Direct variable declaration: SomeView v = findViewById(...)
        if (parent is UVariable) {
            val declaredType = parent.type
            if (declaredType.canonicalText != VIEW_CLASS) {
                return declaredType
            }
            return null
        }

        // Case 2: Parenthesized expression
        if (parent is UParenthesizedExpression) {
            return getAssignedType(node)
        }

        // Case 3: Return statement in a method with a specific return type
        if (parent is UReturnExpression) {
            val method = node.getParentOfType<UMethod>(true) ?: return null
            val returnType = method.returnType ?: return null
            if (returnType.canonicalText != VIEW_CLASS) {
                return returnType
            }
        }

        return null
    }
}