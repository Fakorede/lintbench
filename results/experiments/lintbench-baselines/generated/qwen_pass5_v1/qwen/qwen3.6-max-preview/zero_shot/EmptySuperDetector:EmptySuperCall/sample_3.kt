package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.UElementHandler

class EmptySuperDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() as? PsiMethod ?: return
                if (!hasEmptySuperAnnotation(method)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call super implementation of method annotated with @EmptySuper"
                )
            }
        }
    }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.modifierList?.annotations?.any { annotation ->
            annotation.qualifiedName?.endsWith("EmptySuper") == true
        } == true
    }

    companion object {
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}