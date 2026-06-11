package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod

val SUSPICIOUS_DIFFUTIL_EQUALITY = Issue.create(
    id = "SuspiciousDiffUtilEquality",
    briefDescription = "Suspicious DiffUtil Equality",
    explanation = """
        `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.
    """,
    category = Category.CORRECTNESS,
    priority = 6,
    severity = Severity.WARNING,
    implementation = Implementation(
        DiffUtilDetector::class.java,
        Scope.JAVA_FILE_SCOPE
    )
)

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("areContentsTheSame")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "areContentsTheSame") {
            val arguments = node.valueArguments
            if (arguments.size != 2) {
                return
            }
            val firstArg = arguments[0]
            val secondArg = arguments[1]

            // Check for identity comparison
            if ((firstArg as? UCallExpression)?.methodName == "==" ||
                (secondArg as? UCallExpression)?.methodName == "==") {
                context.report(
                    SUSPICIOUS_DIFFUTIL_EQUALITY,
                    node,
                    context.getLocation(node),
                    "Suspicious use of identity comparison in areContentsTheSame"
                )
            }

            // Check for equals method call
            if ((firstArg as? UCallExpression)?.methodName == "equals" ||
                (secondArg as? UCallExpression)?.methodName == "equals") {
                context.report(
                    SUSPICIOUS_DIFFUTIL_EQUALITY,
                    node,
                    context.getLocation(node),
                    "Suspicious use of equals method in areContentsTheSame"
                )
            }
        }
    }

    override fun visitMethod(context: JavaContext, method: UMethod) {
        if (method.name == "areContentsTheSame") {
            val body = method.bodyExpression
            if (body is UCallExpression && (body.methodName == "==" || body.methodName == "equals")) {
                context.report(
                    SUSPICIOUS_DIFFUTIL_EQUALITY,
                    method,
                    context.getLocation(method),
                    "Suspicious use of identity comparison or equals in areContentsTheSame"
                )
            }
        }
    }
}