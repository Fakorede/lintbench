package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class LogDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MismatchedLogTags",
            briefDescription = "Mismatched log tags or levels",
            explanation = """
                When guarding a `Log.v(tag, ...)` call with `Log.isLoggable(tag)`, the tag passed to both calls should be the same. Similarly, the level passed in to `Log.isLoggable` should typically match the type of `Log` call.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LogDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val LOG_METHODS = listOf("v", "d", "i", "w", "e")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return LOG_METHODS + "isLoggable"
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (methodName == "isLoggable") {
            checkIsLoggable(context, node)
        } else if (LOG_METHODS.contains(methodName)) {
            checkLogMethod(context, node, methodName)
        }
    }

    private fun checkIsLoggable(context: JavaContext, node: UCallExpression) {
        val tag = node.valueArguments.firstOrNull()?.sourcePsi?.text
        val logLevelNode = findCorrespondingLogLevel(node.uastParent as? UClass ?: return)
        if (logLevelNode != null && LOG_METHODS.contains(logLevelNode.methodName)) {
            checkLogMethod(context, logLevelNode, logLevelNode.methodName)
        }
    }

    private fun checkLogMethod(context: JavaContext, node: UCallExpression, methodName: String) {
        val tag = node.valueArguments.firstOrNull()?.sourcePsi?.text
        val isGuardedByIsLoggable = findGuardingIsLoggable(node.uastParent as? UClass ?: return)
        if (isGuardedByIsLoggable != null && isGuardedByIsLoggable.tag != tag) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "The tag passed to Log.isLoggable should match the tag in the log call"
            )
        }
    }

    private fun findCorrespondingLogLevel(uClass: UClass): UCallExpression? {
        val statements = uClass.body?.statements ?: return null
        for (statement in statements) {
            if (statement is UIfExpression && statement.condition is UCallExpression &&
                (statement.condition as UCallExpression).methodName == "isLoggable") {
                val trueBranch = (statement.thenExpression as? UBlockExpression)?.expressions?.firstOrNull() ?: continue
                if (trueBranch is UCallExpression && LOG_METHODS.contains(trueBranch.methodName)) {
                    return trueBranch
                }
            }
        }
        return null
    }

    private fun findGuardingIsLoggable(uClass: UClass): GuardedLog? {
        val statements = uClass.body?.statements ?: return null
        for (statement in statements) {
            if (statement is UIfExpression && statement.condition is UCallExpression &&
                (statement.condition as UCallExpression).methodName == "isLoggable") {
                val tag = (statement.condition as UCallExpression).valueArguments.firstOrNull()?.sourcePsi?.text ?: continue
                return GuardedLog(tag, statement)
            }
        }
        return null
    }

    private data class GuardedLog(val tag: String?, val node: UElement)

}