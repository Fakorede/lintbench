package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        val EMPTY_SUPER_CALL = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` should not have their super implementation called when overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    internal fun isAnnotatedWithEmptySuper(method: UMethod): Boolean {
        return method.annotations.any { it.qualifiedName == "com.example.EmptySuper" }
    }

    override fun visitMethod(context: JavaContext, node: UMethod) {
        val overriddenMethods = context.evaluator.getOverriddenMethods(node)
        for (overridden in overriddenMethods) {
            if (isAnnotatedWithEmptySuper(overridden as UMethod)) {
                node.accept(object : UastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = overridden.name
                        if (node.receiver == null && methodName == node.methodName.asString()) {
                            context.report(
                                EMPTY_SUPER_CALL,
                                node,
                                context.getLocation(node),
                                "Do not call super implementation of method annotated with @EmptySuper"
                            )
                        }
                        return super.visitCallExpression(node)
                    }

                    override fun visitElement(node: UElement): Boolean = true
                })
            }
        }
    }
}