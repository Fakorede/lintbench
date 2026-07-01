package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also \
                call the super implementation, either because it is empty, or perhaps it contains \
                code not intended to be run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used; logic is in visitCallExpression
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Check if this is a super.method() call
                val receiver = (node.uastParent as? UQualifiedReferenceExpression)?.receiver
                    ?: return
                if (receiver !is USuperExpression) return

                // Resolve the called method
                val resolvedMethod: PsiMethod = node.resolve() ?: return

                // Check if the resolved method (in the superclass) has @EmptySuper annotation
                val hasEmptySuper = resolvedMethod.annotations.any { annotation ->
                    annotation.qualifiedName == EMPTY_SUPER_ANNOTATION
                }

                if (!hasEmptySuper) return

                // Find the enclosing method to provide better error context
                val enclosingMethod = node.getParentOfType<UMethod>(strict = true)

                val methodName = resolvedMethod.name
                context.report(
                    ISSUE,
                    node,
                    context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                    "No need to call `super.$methodName`; the super method is defined to be empty",
                )
            }
        }
}