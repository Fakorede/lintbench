package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UExpressionStatement
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() as? PsiMethod ?: return
                if (!context.evaluator.hasAnnotation(method, EMPTY_SUPER_ANNOTATION, EMPTY_SUPER_ANNOTATION_SIMPLE)) return

                val targetNode = if (node.uastParent is UExpressionStatement) node.uastParent else node
                val fix = LintFix.create()
                    .name("Remove super call")
                    .replace()
                    .range(context.getLocation(targetNode))
                    .with("")
                    .reformat(true)
                    .build()

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call super method annotated with @EmptySuper",
                    fix
                )
            }
        }
    }

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"
        private const val EMPTY_SUPER_ANNOTATION_SIMPLE = "EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "Methods annotated with `@EmptySuper` should not have their super implementation called from overriding methods, as the super method is empty or contains code not intended to run when overridden.",
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