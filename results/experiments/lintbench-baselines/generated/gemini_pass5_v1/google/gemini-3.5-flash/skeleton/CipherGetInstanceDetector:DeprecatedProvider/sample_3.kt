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
import org.jetbrains.uast.UExpression

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
                The Bouncy Castle security provider (`BC`) is deprecated and should not be explicitly requested. \
                It will not be provided when `targetSdkVersion` is P or higher.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName.startsWith("java.security.") ||
            qualifiedName.startsWith("javax.crypto.") ||
            qualifiedName.startsWith("javax.net.ssl.")
        ) {
            for (argument in node.valueArguments) {
                if (isBcProvider(argument)) {
                    val incident = Incident(context, ISSUE)
                        .at(node)
                        .message("The `BC` provider is deprecated and of limited use on Android. See https://goo.gle/DeprecatedProvider for details.")
                    context.report(incident)
                    break
                }
            }
        }
    }

    private fun isBcProvider(expression: UExpression): Boolean {
        val constant = expression.evaluate()
        if (constant is String && constant.equals("BC", ignoreCase = true)) {
            return true
        }

        if (expression is UCallExpression) {
            val method = expression.resolve()
            if (method != null && method.name == "getProvider") {
                val containingClass = method.containingClass
                if (containingClass != null && containingClass.qualifiedName == "java.security.Security") {
                    val args = expression.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0].evaluate()
                        if (firstArg is String && firstArg.equals("BC", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.project.targetSdkVersion.featureLevel >= 28
    }
}