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
import org.jetbrains.uast.UClass
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

    override fun applicableSuperClasses(): List<String>? {
        // We want to visit all classes, but applicableSuperClasses requires specific super classes.
        // Instead, we'll use getApplicableUastTypes to visit all methods.
        return null
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Check if this method overrides a method annotated with @EmptySuper
                val evaluator = context.evaluator
                val superMethod = evaluator.getSuperMethod(node) ?: return

                // Check if the super method has @EmptySuper annotation
                val annotations = evaluator.getAllAnnotations(superMethod, inHierarchy = false)
                val hasEmptySuper = annotations.any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
                if (!hasEmptySuper) return

                // Now look for super.methodName() calls within this method
                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val receiver = node.receiver
                        if (receiver is USuperExpression) {
                            val calledMethod = node.resolve()
                            if (calledMethod != null && calledMethod == superMethod) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                                    "No need to call `super.${superMethod.name}()`; the super method is defined to be empty"
                                )
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }
    }
}