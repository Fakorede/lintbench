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
            "androidx.credentials.PublicKeyCredential"

        private const val CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"

        private const val GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS =
            "androidx.credentials.GetPublicKeyCredentialOption"

        private val APPLICABLE_CONSTRUCTOR_TYPES = listOf(
            PUBLIC_KEY_CREDENTIAL_CLASS,
            CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS,
            GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS,
        )

        private const val MESSAGE =
            "Credential Manager API supports creating public key credential (Passkeys) " +
                "starting Android 9 or higher. Please check for the Android version " +
                "before calling the method."

        // Android 9 is API level 28
        private const val MIN_REQUIRED_API = 28
    }

    override fun getApplicableConstructorTypes(): List<String> = APPLICABLE_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!context.isSuppressedWithComment(node, ISSUE)) {
            val minSdk = context.mainProject.minSdk
            if (minSdk < MIN_REQUIRED_API) {
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = MESSAGE,
                )
            }
        }
    }
}