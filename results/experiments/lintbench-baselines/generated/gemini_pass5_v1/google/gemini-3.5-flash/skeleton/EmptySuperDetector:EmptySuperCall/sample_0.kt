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
            explanation = "Super methods annotated with `@EmptySuper` are empty or contain code " +
                    "that should not be called when overridden. Overriding methods should " +
                    "not call this super implementation.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No-op, we only need to inspect call expressions.
            }

            override fun visitCallExpression(node: UCallExpression) {
                val receiver = node.receiver
                if (receiver is USuperExpression) {
                    val method = node.resolve() ?: return
                    if (hasEmptySuperAnnotation(method, context)) {
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

    private fun hasEmptySuperAnnotation(method: PsiMethod, context: JavaContext): Boolean {
        return context.evaluator.getAllAnnotations(method, false).any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "androidx.annotation.EmptySuper" ||
                    qualifiedName == "android.annotation.EmptySuper" ||
                    qualifiedName?.endsWith(".EmptySuper") == true
        }
    }
}