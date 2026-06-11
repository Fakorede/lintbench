package com.android.tools.lint.checks

import com.android.SdkConstants.LOG_CLASS
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class LogDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val UNCONDITIONAL_LOGGING: Issue = Issue.create(
            id = "UnconditionalLogging",
            briefDescription = "Unconditional logging calls",
            explanation = """
                Unconditional logging calls can leak sensitive information and impact performance. 
                It is recommended to wrap logging statements with a check for `BuildConfig.DEBUG`.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LogDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("d", "e", "w", "i", "v")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        if (!isLoggingMethod(node.resolve())) {
            return
        }
        
        val parent = node.uastParent ?: return
        
        if (parent is UIfExpression && isDebugCheck(parent.condition)) {
            return
        }

        context.report(
            UNCONDITIONAL_LOGGING,
            node,
            context.getLocation(node),
            "Unconditional logging call. Consider wrapping with a check for BuildConfig.DEBUG"
        )
    }
    
    @VisibleForTesting
    fun isLoggingMethod(method: UElement?): Boolean {
        if (method !is UastJavaMethod) return false

        val className = method.containingClass?.qualifiedName ?: return false
        return className == LOG_CLASS && (method.name in listOf("d", "e", "w", "i", "v"))
    }

    @VisibleForTesting
    fun isDebugCheck(condition: UElement?): Boolean {
        if (condition !is UQualifiedReferenceExpression) return false

        val methodName = condition.selectorName ?: return false
        if (methodName != "DEBUG") return false
        
        val containingClass = condition.receiver as? UClass ?: return false
        return containingClass.qualifiedName == "BuildConfig"
    }
}