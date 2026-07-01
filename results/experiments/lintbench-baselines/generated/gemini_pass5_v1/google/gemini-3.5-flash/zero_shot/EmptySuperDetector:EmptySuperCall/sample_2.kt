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
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver is USuperExpression) {
                    val method = node.resolve() ?: return
                    if (hasEmptySuperAnnotation(method)) {
                        val message = "No need to call `super.${method.name}` because it is annotated with `@EmptySuper`"
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            message
                        )
                    }
                }
            }
        }
    }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.hasAnnotation("androidx.annotation.EmptySuper") ||
                method.annotations.any { it.qualifiedName?.endsWith(".EmptySuper") == true }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also call \
                the super implementation, either because it is empty, or perhaps it contains code \
                not intended to be run when the method is overridden.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}