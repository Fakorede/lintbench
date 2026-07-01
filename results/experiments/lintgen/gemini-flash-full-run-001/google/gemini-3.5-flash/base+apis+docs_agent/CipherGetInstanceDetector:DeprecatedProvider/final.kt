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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                Avoid specifying the provider explicitly, or transition to AndroidOpenSSL (Conscrypt) instead.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (!qualifiedName.startsWith("java.security.") && !qualifiedName.startsWith("javax.crypto.")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.size < 2) {
            return
        }

        val providerArg = arguments[1]
        val providerValue = ConstantEvaluator.evaluate(context, providerArg)
        if (providerValue == "BC") {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "The `BC` provider is deprecated and will not be provided when `targetSdkVersion` is P or higher"
            )
        }
    }
}