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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION = "EmptySuper"
        private const val ANNOTATION_FULL = "androidx.annotation.EmptySuper"

        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "For methods annotated with @EmptySuper, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used; super calls are detected directly.
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (node.isConstructorCall()) return
                if (node.receiver !is USuperExpression) return

                val calledMethod = node.resolve() as? PsiMethod ?: return
                if (!isEmptySuper(calledMethod, context)) return

                val message =
                    "Calling a method annotated @EmptySuper is not allowed; the super implementation is either empty or not intended to be invoked."
                context.report(ISSUE, node, context.getLocation(node), message)
            }
        }

    private fun isEmptySuper(method: PsiMethod, context: JavaContext): Boolean {
        return context.evaluator.getAnnotation(method, ANNOTATION, ANNOTATION_FULL) != null
    }
}