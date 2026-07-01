package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) {
                    return
                }

                val method = node.getParentOfType(UMethod::class.java) ?: return
                val superMethod = node.resolve() as? PsiMethod ?: return
                if (superMethod.isConstructor) {
                    return
                }

                val psiMethod = method.javaPsi ?: return
                if (superMethod !in psiMethod.findSuperMethods()) {
                    return
                }

                if (hasEmptySuperAnnotation(context.evaluator, superMethod)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling super.${superMethod.name} when the overridden method is annotated with @EmptySuper"
                    )
                }
            }
        }
    }

    private fun hasEmptySuperAnnotation(evaluator: JavaEvaluator, method: PsiMethod): Boolean {
        if (evaluator.findAnnotation(method, "androidx.annotation.EmptySuper") != null) {
            return true
        }
        return method.modifierList.annotations.any { annotation ->
            annotation.qualifiedName?.substringAfterLast('.') == "EmptySuper"
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "EmptySuperCall",
            "Calling an empty super method",
            """
                Methods annotated with @EmptySuper indicate that overriding methods should not
                call the super implementation, either because it is empty, or because it
                contains code not intended to be run when the method is overridden.
            """.trimIndent(),
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}