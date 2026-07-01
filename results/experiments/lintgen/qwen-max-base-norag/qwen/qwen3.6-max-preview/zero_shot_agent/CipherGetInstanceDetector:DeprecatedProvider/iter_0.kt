package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val args = node.valueArguments
        if (args.size == 2) {
            val providerArg = args[1]
            if (context.evaluator.getStringValue(providerArg) == "BC") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(providerArg),
                    "Using the BC provider is deprecated and will be removed in Android P and higher."
                )
            }
        }
    }
}