package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), Detector.UastScanner {

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.receiver is USuperExpression) {
            if (context.evaluator.findAnnotation(method, "EmptySuper") != null) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Do not call super implementation of method annotated with @EmptySuper"
                )
            }
        }
    }

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
}