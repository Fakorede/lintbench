package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

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
            explanation = "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java, UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Only UCallExpression and UQualifiedReferenceExpression are registered.
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkSuperCall(context, node, node, false)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                if (node.receiver !is USuperExpression) return
                val call = node.selector as? UCallExpression ?: return
                checkSuperCall(context, call, node, true)
            }
        }

    private fun checkSuperCall(
        context: JavaContext,
        call: UCallExpression,
        reportNode: UElement,
        knownSuper: Boolean
    ) {
        if (!knownSuper && call.receiver !is USuperExpression) return
        val method = call.resolve() as? PsiMethod ?: return
        if (!method.hasEmptySuperAnnotation()) return
        context.report(
            ISSUE,
            reportNode,
            context.getLocation(reportNode),
            "The super implementation of `${method.name}` is annotated `@EmptySuper` and should not be called."
        )
    }

    private fun PsiMethod.hasEmptySuperAnnotation(): Boolean {
        return annotations.any { it.qualifiedName?.substringAfterLast('.') == "EmptySuper" }
    }
}