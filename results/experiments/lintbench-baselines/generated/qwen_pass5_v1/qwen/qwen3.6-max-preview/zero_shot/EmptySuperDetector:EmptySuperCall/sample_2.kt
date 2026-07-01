package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression

class EmptySuperDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isSuperCall) return

                val method = node.resolve() ?: return
                if (context.evaluator.hasAnnotation(method, "androidx.annotation.EmptySuper", "EmptySuper")) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Do not call `super` for methods annotated with `@EmptySuper`"
                    )
                }
            }
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}