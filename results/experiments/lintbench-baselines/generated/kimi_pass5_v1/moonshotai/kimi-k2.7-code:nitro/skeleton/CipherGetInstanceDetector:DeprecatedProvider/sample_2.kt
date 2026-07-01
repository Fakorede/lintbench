package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
                The `BC` (Bouncy Castle) provider has been deprecated on Android. For apps
                with `targetSdkVersion` 28 (Android P) or higher, the `BC` provider is removed
                and calls such as `Cipher.getInstance(algorithm, "BC")` will fail at runtime.

                Use the default provider by calling `Cipher.getInstance(algorithm)` or
                migrate to a supported provider.
            """.trimIndent(),
            moreInfo = "https://goo.gle/DeprecatedProvider",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TARGET_SDK_P = 28
        private const val BC_PROVIDER = "BC"
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val args: List<UExpression> = node.valueArguments
        if (args.size < 2) {
            return
        }

        val providerArg = args[1]
        val provider = ConstantEvaluator.evaluateString(context, providerArg, false)
        if (provider == BC_PROVIDER) {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "Using BC Provider",
            )
        }
    }

    override fun filterIncident(
        context: Context,
        incident: Incident,
        map: LintMap,
    ): Boolean {
        return context.mainProject.targetSdk >= TARGET_SDK_P
    }
}