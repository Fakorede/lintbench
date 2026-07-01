package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }

                val emptySuperMethods = node.findSuperMethods()
                    .filter { it.hasAnnotation(ANNOTATION_EMPTY_SUPER) }
                if (emptySuperMethods.isEmpty()) {
                    return
                }

                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        if (call.isSuperCall()) {
                            val resolved = call.resolve()
                            if (resolved != null && resolved in emptySuperMethods) {
                                context.report(
                                    ISSUE,
                                    call,
                                    context.getLocation(call),
                                    "Overriding method should not call `super.${node.name}()` because the " +
                                        "super method is annotated `@EmptySuper`"
                                )
                            }
                        }
                        return super.visitCallExpression(call)
                    }
                })
            }
        }
    }

    private fun UCallExpression.isSuperCall(): Boolean {
        if (receiver is USuperExpression) {
            return true
        }
        val parent = uastParent
        if (parent is UQualifiedReferenceExpression &&
            parent.receiver is USuperExpression &&
            parent.selector == this
        ) {
            return true
        }
        return false
    }

    companion object {
        private const val ANNOTATION_EMPTY_SUPER = "androidx.annotation.EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not call the super implementation.
                The super method is either empty or contains code that should not be run when the method is overridden.
                Remove the `super` call from the overriding method.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}