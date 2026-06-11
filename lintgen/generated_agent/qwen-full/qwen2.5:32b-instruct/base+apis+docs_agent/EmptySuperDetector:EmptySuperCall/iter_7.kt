package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "EmptySuper",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` should not call the super implementation in overriding methods, either because it is empty or contains code not intended to be run when overridden.
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

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    private fun isAnnotatedWithEmptySuper(element: UElement): Boolean {
        return element.annotations.any { it.qualifiedName == "com.android.tools.lint.detector.api.EmptySuper" }
    }

    private fun checkForSuperCall(context: JavaContext, method: UMethod) {
        val body = method.bodyExpression as? UBlockExpression ?: return
        for (expr in body.expressions) {
            if (expr is UCallExpression && expr.methodName == "super") {
                context.report(
                    ISSUE,
                    expr,
                    context.getLocation(expr),
                    "Overriding methods should not call super when annotated with @EmptySuper"
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (isAnnotatedWithEmptySuper(node)) {
                    checkForSuperCall(context, node)
                }
            }
        }
    }

}