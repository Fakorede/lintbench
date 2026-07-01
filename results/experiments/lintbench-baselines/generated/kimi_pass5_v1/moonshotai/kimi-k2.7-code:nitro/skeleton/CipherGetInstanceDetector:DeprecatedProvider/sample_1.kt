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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TARGET_SDK_P = 28
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val BC_PROVIDER = "BC"
        private const val BOUNCY_CASTLE_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider"

        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be available when
                `targetSdkVersion` is 28 (Android P) or higher. Use the default provider
                instead of explicitly specifying `BC`.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/DeprecatedProvider",
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val args = node.valueArguments
        if (args.size < 2) {
            return
        }

        val providerArg = args[1]
        if (!isBcProvider(context, providerArg)) {
            return
        }

        val location = context.getLocation(providerArg)
        val message = "The `BC` provider has been deprecated and is removed when targetSdkVersion is P or higher."

        context.report(Incident(ISSUE, node, location, message))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.mainProject.targetSdk >= TARGET_SDK_P
    }

    private fun isBcProvider(context: JavaContext, arg: UExpression): Boolean {
        val providerName = ConstantEvaluator.evaluateString(context, arg, false)
        if (providerName == BC_PROVIDER) {
            return true
        }

        val type = arg.getExpressionType() ?: return false
        return type.canonicalText == BOUNCY_CASTLE_PROVIDER_CLASS
    }
}