package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

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
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No-op: we handle everything in visitCallExpression
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Check if this call expression is a super call (e.g., super.foo(...))
                val qualifiedExpression = node.uastParent as? UQualifiedReferenceExpression
                    ?: return

                val receiver = qualifiedExpression.receiver
                if (receiver !is USuperExpression) {
                    return
                }

                // Resolve the method being called
                val resolvedMethod = node.resolve() ?: return

                // Check if the resolved method has @EmptySuper annotation
                val hasEmptySuperAnnotation = resolvedMethod.hasAnnotation(EMPTY_SUPER_ANNOTATION)
                if (!hasEmptySuperAnnotation) {
                    return
                }

                // Report the issue
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "No need to call `super.${resolvedMethod.name}()`; the super method is defined to be empty",
                )
            }
        }
}