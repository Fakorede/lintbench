package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance", "getProvider")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val evaluator = context.evaluator

        if (methodName == "getProvider") {
            if (evaluator.isMemberInClass(method, "java.security.Security")) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val value = ConstantEvaluator.evaluate(context, args[0])
                    if (value == "BC") {
                        reportIssue(context, node)
                    }
                }
            }
        } else if (methodName == "getInstance") {
            val containingClass = method.containingClass ?: return
            val qualifiedName = containingClass.qualifiedName ?: return
            if (qualifiedName.startsWith("java.security.") || qualifiedName.startsWith("javax.crypto.")) {
                val arguments = node.valueArguments
                if (arguments.size >= 2) {
                    val providerArg = arguments[1]
                    val value = ConstantEvaluator.evaluate(context, providerArg)
                    if (value == "BC") {
                        reportIssue(context, node)
                    }
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "The `BC` provider is deprecated and when targeting Android P or higher will throw an exception."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                
                Avoid explicitly requesting the `BC` provider. Instead, use the default provider by omitting the provider argument.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/DeprecatedProvider"
        )
    }
}