package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() ?: return
                if (!hasEmptySuperAnnotation(method)) return

                context.report(
                    ISSUE,
                    node,
                    context.getCallLocation(node, includeReceiver = true, includeArguments = false),
                    "Do not call super method annotated with @EmptySuper"
                )
            }
        }
    }

    private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { ann ->
            val qName = ann.qualifiedName
            qName == "EmptySuper" || qName?.endsWith(".EmptySuper") == true
        }
    }

    companion object {
        @JvmField
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