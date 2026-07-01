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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val evaluator = context.evaluator

                // Find the super method this method overrides
                val superMethod = findSuperMethodWithEmptySuper(node, evaluator) ?: return

                // Now look for super.methodName() calls within this method body
                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val receiver = node.receiver
                        if (receiver is USuperExpression) {
                            val calledMethod = node.resolve()
                            if (calledMethod != null && isSameMethod(calledMethod, superMethod)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                                    "No need to call `super.${superMethod.name}()`; the super method is defined to be empty"
                                )
                            }
                        }
                        return false
                    }
                })
            }
        }
    }

    private fun findSuperMethodWithEmptySuper(
        node: UMethod,
        evaluator: com.android.tools.lint.detector.api.JavaEvaluator
    ): PsiMethod? {
        val psiMethod = node.javaPsi

        // Get all super methods in the hierarchy
        val superMethods = psiMethod.findSuperMethods(false)
        for (superMethod in superMethods) {
            if (hasEmptySuperAnnotation(superMethod, evaluator)) {
                return superMethod
            }
        }

        // Also check transitively up the hierarchy
        for (superMethod in superMethods) {
            val found = findEmptySuperInHierarchy(superMethod, evaluator)
            if (found != null) return found
        }

        return null
    }

    private fun findEmptySuperInHierarchy(
        method: PsiMethod,
        evaluator: com.android.tools.lint.detector.api.JavaEvaluator
    ): PsiMethod? {
        if (hasEmptySuperAnnotation(method, evaluator)) {
            return method
        }
        val superMethods = method.findSuperMethods(false)
        for (superMethod in superMethods) {
            val found = findEmptySuperInHierarchy(superMethod, evaluator)
            if (found != null) return found
        }
        return null
    }

    private fun hasEmptySuperAnnotation(
        method: PsiMethod,
        evaluator: com.android.tools.lint.detector.api.JavaEvaluator
    ): Boolean {
        val annotations = evaluator.getAllAnnotations(method, inHierarchy = false)
        return annotations.any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
    }

    private fun isSameMethod(method1: PsiMethod, method2: PsiMethod): Boolean {
        return method1 == method2 ||
                (method1.name == method2.name &&
                        method1.containingClass?.qualifiedName == method2.containingClass?.qualifiedName)
    }
}