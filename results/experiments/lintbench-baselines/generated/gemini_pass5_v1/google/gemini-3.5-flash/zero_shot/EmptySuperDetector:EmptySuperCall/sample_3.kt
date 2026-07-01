package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also call \
                the super implementation, either because it is empty, or perhaps it contains \
                code not intended to be run when the method is overridden.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"
        private const val SHORT_EMPTY_SUPER_ANNOTATION = "EmptySuper"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val receiver = node.receiver
                if (receiver is USuperExpression) {
                    val method = node.resolve() ?: return
                    if (hasEmptySuperAnnotation(context, method)) {
                        val methodName = method.name
                        val message = "No need to call `super.$methodName`; the super method is empty"
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

    private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        if (evaluator.hasAnnotation(method, EMPTY_SUPER_ANNOTATION)) {
            return true
        }
        for (annotation in method.annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && (qualifiedName == EMPTY_SUPER_ANNOTATION || qualifiedName.endsWith(".$SHORT_EMPTY_SUPER_ANNOTATION") || qualifiedName == SHORT_EMPTY_SUPER_ANNOTATION)) {
                return true
            }
        }
        return false
    }
}