package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.callKind != UastCallKind.METHOD_CALL) return
                if (node.receiver !is USuperExpression) return

                val method = node.resolve() as? PsiMethod ?: return
                val hasEmptySuper = method.annotations.any { ann ->
                    val qName = ann.qualifiedName
                    qName == "EmptySuper" || qName?.endsWith(".EmptySuper") == true
                }

                if (hasEmptySuper) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not call super implementation of method annotated with @EmptySuper"
                    )
                }
            }
        }
    }
}