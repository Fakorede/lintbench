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
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance", "getProvider")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val name = method.name
        if (name == "getProvider") {
            if (!context.evaluator.isMemberInClass(method, "java.security.Security")) {
                return
            }
            val arg = node.valueArguments.firstOrNull() ?: return
            val value = arg.evaluate() as? String
            if (value == "BC") {
                report(context, node)
            }
        } else if (name == "getInstance") {
            val containingClass = method.containingClass?.qualifiedName ?: return
            if (!containingClass.startsWith("java.security.") && !containingClass.startsWith("javax.crypto.")) {
                return
            }
            for (arg in node.valueArguments) {
                val value = arg.evaluate() as? String
                if (value == "BC") {
                    report(context, node)
                    return
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid explicitly requesting the BC provider"
        )
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.project.targetSdkVersion.apiLevel >= 28
    }
}