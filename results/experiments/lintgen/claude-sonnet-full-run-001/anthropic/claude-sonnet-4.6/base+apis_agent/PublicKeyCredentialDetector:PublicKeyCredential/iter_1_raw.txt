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
import org.jetbrains.uast.UElement

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 or higher. Please check for the Android version before \
                calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"

        private val TARGET_CONSTRUCTORS = listOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return TARGET_CONSTRUCTORS
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isVersionChecked(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Credential Manager API supports creating public key credential (Passkeys) " +
                        "starting Android 9 or higher. Please check for the Android version " +
                        "before calling the method."
            )
        }
    }

    private fun isVersionChecked(context: JavaContext, node: UElement): Boolean {
        // Android 9 is API level 28
        val requiredApi = 28
        return VersionChecks.isWithinVersionCheckConditional(context, node, requiredApi) ||
                VersionChecks.isPrecededByVersionCheckExit(context, node, requiredApi)
    }
}