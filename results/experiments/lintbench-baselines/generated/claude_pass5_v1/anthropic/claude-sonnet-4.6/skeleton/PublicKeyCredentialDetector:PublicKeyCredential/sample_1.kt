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

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Credential Manager API supports creating public key credential " +
                "(Passkeys) starting Android 9 or higher. Please check for the Android version " +
                "before calling the method.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"

        private const val MIN_SDK_VERSION = 28 // Android 9 (Pie)
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(PUBLIC_KEY_CREDENTIAL_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!context.isSdkVersionAtLeast(MIN_SDK_VERSION)) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(node),
                message = "Credential Manager API supports creating public key credential " +
                    "(Passkeys) starting Android 9 or higher. Please check for the Android " +
                    "version before calling the method.",
                scope = node,
            )
        }
    }

    private fun JavaContext.isSdkVersionAtLeast(version: Int): Boolean {
        return mainProject.minSdk >= version
    }
}