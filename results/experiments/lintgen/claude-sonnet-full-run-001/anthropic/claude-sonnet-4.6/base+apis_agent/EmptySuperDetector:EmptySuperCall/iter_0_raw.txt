package com.android.tools.lint.checks

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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also \
                call the super implementation, either because it is empty, or perhaps it \
                contains code not intended to be run when the method is overridden.
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

    override fun getApplicableMethodNames(): List<String> = listOf("super")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // This approach won't work well for super calls; use getApplicableUastTypes instead
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : AbstractUastVisitor() {
        override fun visitMethod(node: UMethod): Boolean {
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.isMethodCall()) {
                        val receiver = node.receiver
                        // Check if this is a super call
                        if (receiver != null && receiver.asSourceString() == "super" ||
                            isSuperCall(node)
                        ) {
                            val resolvedMethod = node.resolve() ?: return false
                            if (hasEmptySuperAnnotation(context, resolvedMethod)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "No need to call `super.${node.methodName}()`; the super method is defined to be empty"
                                )
                            }
                        }
                    }
                    return false
                }
            })
            return false
        }
    }

    private fun isSuperCall(node: UCallExpression): Boolean {
        val sourcePsi = node.sourcePsi ?: return false
        val text = sourcePsi.text ?: return false
        return text.startsWith("super.")
    }

    private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.getAllAnnotations(method, inHierarchy = false)
            .any { annotation ->
                annotation.qualifiedName == EMPTY_SUPER_ANNOTATION
            }
    }
}