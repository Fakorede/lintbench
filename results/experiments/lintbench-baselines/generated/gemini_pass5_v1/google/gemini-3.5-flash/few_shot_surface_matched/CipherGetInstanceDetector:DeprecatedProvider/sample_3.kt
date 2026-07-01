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
        @JvmField
        val DEPRECATED_PROVIDER = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames() = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!qualifiedName.startsWith("java.security.") && !qualifiedName.startsWith("javax.crypto.")) {
            return
        }

        val parameters = method.parameterList.parameters
        var providerArgIndex = -1
        for (i in parameters.indices) {
            val param = parameters[i]
            val type = param.type.canonicalText
            if (type == "java.lang.String" && param.name == "provider") {
                providerArgIndex = i
                break
            }
        }

        if (providerArgIndex == -1 || providerArgIndex >= node.valueArgumentCount) {
            return
        }

        val providerArg = node.valueArguments[providerArgIndex]
        val providerValue = providerArg.evaluate() as? String
        if (providerValue != null && providerValue.equals("BC", ignoreCase = true)) {
            val incident = Incident(
                DEPRECATED_PROVIDER,
                providerArg,
                context.getLocation(providerArg),
                "The `BC` provider is deprecated and should not be used"
            )
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.mainProject.targetSdkVersion.apiLevel >= 28
    }
}