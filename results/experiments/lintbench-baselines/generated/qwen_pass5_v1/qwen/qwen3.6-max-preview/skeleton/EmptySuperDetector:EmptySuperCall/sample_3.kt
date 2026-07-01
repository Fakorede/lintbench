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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression

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
            explanation = "Methods annotated with @EmptySuper should not have their super implementation called from overriding methods. " +
                "The super method is intentionally empty or contains code not meant to be executed when overridden.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used for this detector
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() ?: return

                val hasEmptySuperAnnotation = method.annotations.any { annotation ->
                    annotation.qualifiedName?.endsWith(".EmptySuper") == true
                }

                if (hasEmptySuperAnnotation) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Do not call super method annotated with @EmptySuper"
                    )
                }
            }
        }
}