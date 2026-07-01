package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EMPTY_SUPER = "EmptySuper"

        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not
                call the super implementation, either because it is empty, or because it
                contains code not intended to be run when the method is overridden.
                Remove the `super.` call from the overriding method.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Only call expressions are inspected.
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return

                val calledMethod = node.resolve() ?: return
                if (!calledMethod.hasAnnotation(EMPTY_SUPER)) return

                val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return
                val containingClass = containingMethod.psi.containingClass ?: return
                if (!context.evaluator.overrides(
                        containingMethod.psi,
                        calledMethod,
                        containingClass,
                        false
                    )
                ) {
                    return
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling `super.${calledMethod.name}` is not recommended: the " +
                            "super implementation is annotated with `@$EMPTY_SUPER` and " +
                            "should not be invoked by overriding methods."
                )
            }
        }

    private fun PsiMethod.hasAnnotation(name: String): Boolean {
        return modifierList?.annotations?.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == name || qualifiedName?.endsWith(".$name") == true
        } ?: false
    }
}