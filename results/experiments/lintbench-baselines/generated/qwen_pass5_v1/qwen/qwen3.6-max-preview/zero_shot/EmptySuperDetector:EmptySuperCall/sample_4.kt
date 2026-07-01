package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), UastScanner {

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "EmptySuper"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() as? PsiModifierListOwner ?: return

                if (context.evaluator.hasAnnotation(method, EMPTY_SUPER_ANNOTATION)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not call super method annotated with @EmptySuper"
                    )
                }
            }
        }
    }
}