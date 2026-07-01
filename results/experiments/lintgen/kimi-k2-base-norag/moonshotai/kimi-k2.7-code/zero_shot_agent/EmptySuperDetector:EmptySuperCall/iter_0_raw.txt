package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return

                val receiver = node.receiver ?: return
                if (receiver !is USuperExpression) return

                val calledMethod = node.resolve() ?: return
                if (!calledMethod.hasAnnotation(ANNOTATION)) return

                val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return
                val superMethods = containingMethod.findSuperMethods(false)
                if (superMethods.none { it == calledMethod }) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
        }

    private fun PsiMethod.hasAnnotation(name: String): Boolean {
        return modifierList?.annotations?.any {
            it.name == name || it.qualifiedName?.endsWith(".$name") == true
        } == true
    }

    companion object {
        private const val ANNOTATION = "EmptySuper"
        private const val MESSAGE = "Calling an empty super method"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper should not be invoked via super
                from overriding methods, because the super implementation is empty
                or contains code that should not run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}