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
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

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
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UastFacade? = null

    override fun visitMethod(context: JavaContext, node: UMethod) {
        // Check if this method overrides a super method annotated with @EmptySuper
        val evaluator = context.evaluator
        val superMethod = findSuperMethodWithEmptySuper(context, node) ?: return

        // Visit the method body looking for super.methodName() calls
        node.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkSuperCall(context, node, superMethod)
                return false
            }
        })
    }

    private fun findSuperMethodWithEmptySuper(context: JavaContext, method: UMethod): PsiMethod? {
        val evaluator = context.evaluator
        val superMethods = method.findSuperMethods()
        for (superMethod in superMethods) {
            if (evaluator.getAllAnnotations(superMethod, true)
                    .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
            ) {
                return superMethod
            }
        }
        return null
    }

    private fun checkSuperCall(
        context: JavaContext,
        node: UCallExpression,
        superMethod: PsiMethod
    ) {
        // Check if the receiver is a super expression
        val receiver = node.receiver
        if (receiver !is USuperExpression) return

        // Check that the method name matches
        if (node.methodName != superMethod.name) return

        // Resolve to confirm it's actually calling the super method
        val resolvedMethod = node.resolve() ?: return
        if (!context.evaluator.getAllAnnotations(resolvedMethod, true)
                .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
        ) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "No need to call `super.${superMethod.name}()`; the super method is defined to be empty"
        )
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // Handled via visitMethod + visitor above
    }
}