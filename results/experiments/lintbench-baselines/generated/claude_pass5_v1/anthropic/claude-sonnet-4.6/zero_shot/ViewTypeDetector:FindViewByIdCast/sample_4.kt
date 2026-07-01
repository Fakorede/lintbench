package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isMethodCall

/**
 * Detector for FindViewByIdCast: suggests adding explicit casts to
 * `findViewById` calls to ensure compatibility when upgrading to Java 8.
 */
class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        val FIND_VIEW_BY_ID = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, \
                which means that most of the time you can leave out explicit casts and \
                just assign the result of the `findViewById` call to variables of \
                specific view classes.

                However, due to language changes between Java 7 and 8, this change may \
                cause code to not compile without explicit casts. This lint check looks \
                for these scenarios and suggests casts to be added now such that the \
                code will continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FIND_VIEW_BY_ID_METHOD = "findViewById"
        private const val VIEW_CLASS = "android.view.View"
        private const val ACTIVITY_CLASS = "android.app.Activity"
        private const val DIALOG_CLASS = "android.app.Dialog"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only interested in calls to View.findViewById, Activity.findViewById, Dialog.findViewById
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!isRelevantClass(context, qualifiedName)) return

        // Check if the call is already inside an explicit cast
        val parent = node.uastParent ?: return

        // If the parent is already an explicit cast, no need to warn
        if (parent is UBinaryExpressionWithType) return

        // Check if the result is being used in a context that requires a specific type
        // We look for cases where the result is used without an explicit cast but
        // is assigned to a typed variable or passed to a typed method parameter

        // Case 1: Assignment to a typed variable without cast
        // e.g., Button button = findViewById(R.id.button); // no cast
        if (parent is ULocalVariable) {
            val typeRef = parent.typeReference
            if (typeRef != null) {
                val typeName = typeRef.getQualifiedName()
                if (typeName != null && typeName != VIEW_CLASS && isViewSubtype(context, typeName)) {
                    reportIssue(context, node, typeName)
                }
            }
            return
        }

        // Case 2: The call is on the right side of an assignment
        if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
            val leftOperand = parent.leftOperand
            val leftType = leftOperand.getExpressionType()
            if (leftType != null) {
                val typeName = leftType.canonicalText
                if (typeName != VIEW_CLASS && isViewSubtype(context, typeName)) {
                    reportIssue(context, node, typeName)
                }
            }
            return
        }

        // Case 3: Used as a method argument without cast
        if (parent is UCallExpression) {
            // This is harder to check without full type resolution,
            // but we can skip for now as the primary use case is assignment
            return
        }

        // Case 4: Returned from a method
        if (parent is UReturnExpression) {
            val containingMethod = node.getContainingUMethod() ?: return
            val returnType = containingMethod.returnType ?: return
            val typeName = returnType.canonicalText
            if (typeName != VIEW_CLASS && isViewSubtype(context, typeName)) {
                reportIssue(context, node, typeName)
            }
            return
        }
    }

    private fun isRelevantClass(context: JavaContext, qualifiedName: String): Boolean {
        // Check exact matches first
        if (qualifiedName == VIEW_CLASS ||
            qualifiedName == ACTIVITY_CLASS ||
            qualifiedName == DIALOG_CLASS
        ) {
            return true
        }
        // We also accept subclasses, but for simplicity check known patterns
        return true
    }

    private fun isViewSubtype(context: JavaContext, typeName: String): Boolean {
        // Basic check: if it's in android.widget or android.view package, it's likely a View
        if (typeName.startsWith("android.widget.") ||
            typeName.startsWith("android.view.") ||
            typeName.startsWith("android.webkit.") ||
            typeName.startsWith("android.opengl.")
        ) {
            return true
        }
        // Also check for common custom view patterns
        if (typeName == VIEW_CLASS) return false
        // For non-Android classes, we'd need type resolution to be sure
        // Return false to avoid false positives
        return false
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression, targetType: String) {
        val simpleName = targetType.substringAfterLast('.')
        context.report(
            FIND_VIEW_BY_ID,
            node,
            context.getLocation(node),
            "Add explicit cast here; will be required in Java 8",
            fix().name("Add cast to $simpleName")
                .replace()
                .reformat(true)
                .shortenNames()
                .with("($simpleName) ${node.asSourceString()}")
                .build()
        )
    }
}