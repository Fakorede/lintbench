package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaElementVisitor
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(CREATE_CREDENTIAL_METHOD)

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        visitor: JavaElementVisitor
    ) {
        val hasPublicKeyRequest = call.valueArguments.any { arg ->
            arg.getExpressionType()?.canonicalText == CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST
        }
        if (!hasPublicKeyRequest) {
            return
        }

        if (!VersionChecks.isWithinVersionCheck(call, ANDROID_9_API_LEVEL)) {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Creating public key credentials (passkeys) is only supported on Android 9 (API 28) and higher. " +
                    "Add a `Build.VERSION.SDK_INT` check before calling `createCredential`."
            )
        }
    }

    companion object {
        private const val CREATE_CREDENTIAL_METHOD = "createCredential"
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val ANDROID_9_API_LEVEL = 28

        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager supports creating public key credentials (Passkeys)
                starting with Android 9 (API 28) or higher. Calls to create a public key
                credential should be guarded with a version check to avoid runtime issues
                on older devices.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}