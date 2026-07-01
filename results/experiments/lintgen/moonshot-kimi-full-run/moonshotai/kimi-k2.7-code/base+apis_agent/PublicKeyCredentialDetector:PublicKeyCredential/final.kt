package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "androidx.credentials.CredentialManager")) {
            return
        }

        if (context.mainProject.minSdk >= MIN_API) {
            return
        }

        if (!hasPublicKeyCredentialRequest(node)) {
            return
        }

        if (VersionChecks.isWithinVersionCheck(node, MIN_API) ||
            VersionChecks.isPrecededByVersionCheck(node, MIN_API)
        ) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential is only supported on Android 9 (API 28) and higher. " +
                "Check `Build.VERSION.SDK_INT` before calling `createCredential()`."
        )
    }

    private fun hasPublicKeyCredentialRequest(node: UCallExpression): Boolean {
        return node.valueArguments.any { arg ->
            arg.getExpressionType()?.canonicalText?.removeSuffix("?") == REQUEST_TYPE
        }
    }

    companion object {
        private const val MIN_API = 28
        private const val REQUEST_TYPE = "androidx.credentials.CreatePublicKeyCredentialRequest"

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Public key credential creation requires Android 9 or higher",
            explanation = """
                The Credential Manager API supports creating public key credentials (passkeys) only on
                Android 9 (API 28) and higher. You must check `Build.VERSION.SDK_INT` before calling
                `CredentialManager.createCredential(...)` with a `CreatePublicKeyCredentialRequest`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}