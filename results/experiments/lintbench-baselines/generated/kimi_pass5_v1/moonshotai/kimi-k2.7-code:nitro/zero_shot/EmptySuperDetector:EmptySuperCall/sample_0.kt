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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.receiver !is USuperExpression) return

                val superMethod = node.resolve() ?: return
                if (!context.evaluator.hasAnnotation(superMethod, "androidx.annotation.EmptySuper")) {
                    return
                }

                val overridingMethod = node.getParentOfType(UMethod::class.java) ?: return
                if (!overridingMethod.findSuperMethods().contains(superMethod)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Overriding method should not call super.${superMethod.name}(), which is annotated @EmptySuper"
                )
            }
        }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper have an empty super implementation or contain code
                that should not run when the method is overridden. Overriding methods must not invoke
                super.<method>().
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}