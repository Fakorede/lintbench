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
                "(Passkeys) starting Android 9 or higher. Please check for the Android " +
                "version before calling the method.",
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
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        PUBLIC_KEY_CREDENTIAL_CLASS,
        CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS,
        GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS,
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!isVersionCheckSurrounding(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Credential Manager API supports creating public key credential (Passkeys) " +
                    "starting Android 9 or higher. Please check for the Android version " +
                    "before calling the method.",
            )
        }
    }

    private fun isVersionCheckSurrounding(context: JavaContext, node: UCallExpression): Boolean {
        // Android 9 is API level 28
        val api = 28
        return context.isWithinVersionCheckOf(node, api)
    }

    private fun JavaContext.isWithinVersionCheckOf(node: UCallExpression, api: Int): Boolean {
        return isWithinVersionCheck(node, api)
    }

    private fun JavaContext.isWithinVersionCheck(node: UCallExpression, api: Int): Boolean {
        var current = node.uastParent
        while (current != null) {
            val sourcePsi = current.sourcePsi
            if (sourcePsi != null) {
                val evaluator = evaluator
                if (evaluator.extendsClass(null, "android.os.Build.VERSION", false)) {
                    return true
                }
            }
            current = current.uastParent
        }
        // Use the built-in version check utility from JavaContext
        return try {
            val method = JavaContext::class.java.getMethod(
                "isWithinVersionCheck",
                UCallExpression::class.java,
                Int::class.java
            )
            method.invoke(this, node, api) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }
}