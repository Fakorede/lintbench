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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used for this check
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return
                if (node.methodName == null) return
                val method = node.resolve() ?: return
                if (hasEmptySuperAnnotation(method)) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Calling an empty super method"
                    )
                }
            }
        }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { ann ->
            val qName = ann.qualifiedName
            qName == "EmptySuper" || qName?.endsWith(".EmptySuper") == true
        }
    }
}