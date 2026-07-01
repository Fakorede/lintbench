package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.receiver !is USuperExpression) return

                val method = node.getParentOfType(UMethod::class.java) ?: return
                val superMethod = context.evaluator.getSuperMethod(method) ?: return

                if (superMethod.hasAnnotation(ANNOTATION)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }
        }
    }

    companion object {
        private const val ANNOTATION = "androidx.annotation.EmptySuper"
        private const val MESSAGE = "Calling super on a method annotated with @EmptySuper is unnecessary"

        @JvmField
        val ISSUE = Issue.create(
            "EmptySuperCall",
            "Calling an empty super method",
            """
                A method annotated with @EmptySuper has an empty or intentionally
                non-runnable implementation. Overriding methods should not invoke
                super.<method>(...).
            """.trimIndent(),
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}