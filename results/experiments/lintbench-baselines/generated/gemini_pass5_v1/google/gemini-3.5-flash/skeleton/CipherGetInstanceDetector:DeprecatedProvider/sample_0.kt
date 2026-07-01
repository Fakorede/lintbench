package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The Bouncy Castle security provider (`BC`) is deprecated in Android P and " +
                    "is no longer available for apps that target Android P or higher. " +
                    "Instead, you should use the default provider (by not specifying a provider) " +
                    "or transition to a recommended alternative.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!qualifiedName.startsWith("java.security.") &&
            !qualifiedName.startsWith("javax.crypto.") &&
            !qualifiedName.startsWith("java.security.cert.")) {
            return
        }

        if (node.valueArgumentCount < 2) return

        val secondArg = node.valueArguments[1]
        var isBc = false

        val provider = context.constantEvaluator.evaluate(secondArg) as? String
        if (provider != null && "BC".equals(provider, ignoreCase = true)) {
            isBc = true
        } else {
            val argCall = secondArg as? UCallExpression
            if (argCall != null && argCall.methodName == "getProvider") {
                val getProviderMethod = argCall.resolve()
                if (getProviderMethod != null && getProviderMethod.containingClass?.qualifiedName == "java.security.Security") {
                    if (argCall.valueArgumentCount == 1) {
                        val provName = context.constantEvaluator.evaluate(argCall.valueArguments[0]) as? String
                        if ("BC".equals(provName, ignoreCase = true)) {
                            isBc = true
                        }
                    }
                }
            }
        }

        if (isBc) {
            val incident = Incident(context, ISSUE)
                .location(context.getLocation(secondArg))
                .message("The `BC` provider is deprecated and will not be provided when `targetSdkVersion` is P or higher.")
                .scope(node)
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.project.targetSdkVersion.featureLevel >= 28
    }
}