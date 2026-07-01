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
import com.android.tools.lint.detector.api.ConstantEvaluator
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
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstance")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!qualifiedName.startsWith("java.security.") && !qualifiedName.startsWith("javax.crypto.")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.size >= 2) {
            val providerArg = arguments[1]
            val value = ConstantEvaluator.evaluate(context, providerArg)
            if (value is String && value.equals("BC", ignoreCase = true)) {
                val incident = Incident(
                    ISSUE,
                    providerArg,
                    context.getLocation(providerArg),
                    "The `BC` provider is deprecated and will not be provided when `targetSdkVersion` is P or higher."
                )
                context.report(incident)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}