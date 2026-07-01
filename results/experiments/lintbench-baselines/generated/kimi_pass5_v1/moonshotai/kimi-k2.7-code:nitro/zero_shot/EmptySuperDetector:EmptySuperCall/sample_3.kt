package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) return

                val receiver = node.receiver
                if (receiver !is USuperExpression) return

                val resolved = node.resolve() ?: return
                if (!hasEmptySuperAnnotation(resolved)) return

                val currentMethod = node.getParentOfType<UMethod>(UMethod::class.java) ?: return
                if (!currentMethod.findSuperMethods().contains(resolved)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling a method annotated `@EmptySuper` is not allowed."
                )
            }
        }
    }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            val name = annotation.qualifiedName
            name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` indicate that subclasses should not invoke the
                super implementation. Calling super may run empty code or code that is not intended
                to run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}