package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.client.api.UElementHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not \
                also call the super implementation, either because it is empty, or perhaps \
                it contains code not intended to be run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Find all super calls within this method body
                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val receiver = node.receiver
                        if (receiver is USuperExpression) {
                            val calledMethod = node.resolve() ?: return false
                            // Check if the resolved method (the super method) is annotated with @EmptySuper
                            if (hasEmptySuperAnnotation(context, calledMethod)) {
                                context.report(
                                    issue = ISSUE,
                                    scope = node,
                                    location = context.getCallLocation(
                                        node,
                                        includeReceiver = true,
                                        includeArguments = false
                                    ),
                                    message = "No need to call `super.${node.methodName}()`; the super method is annotated with " +
                                            "`@EmptySuper`, meaning the super implementation is empty or not intended to be called"
                                )
                            }
                        }
                        return false
                    }
                })
            }

            private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
                return context.evaluator.getAllAnnotations(method, inHierarchy = true)
                    .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
            }
        }
    }
}