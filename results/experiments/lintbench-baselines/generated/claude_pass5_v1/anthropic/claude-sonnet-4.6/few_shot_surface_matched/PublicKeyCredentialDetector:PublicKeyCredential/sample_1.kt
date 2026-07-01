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
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"

        private const val CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"

        private const val GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS =
            "androidx.credentials.GetPublicKeyCredentialOption"

        private const val ANDROID_P_API_LEVEL = 28

        @JvmField
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
            ),
            androidSpecific = true,
        )
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
        if (isInsideApiVersionCheck(node, context)) {
            return
        }

        val message =
            "Credential Manager API supports creating public key credential (Passkeys) " +
                "starting Android 9 (API level $ANDROID_P_API_LEVEL) or higher. " +
                "Please check for the Android version before calling this constructor."

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }

    private fun isInsideApiVersionCheck(node: UCallExpression, context: JavaContext): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition
                val conditionText = condition.asSourceString()
                if (referencesApiLevelCheck(conditionText)) {
                    return true
                }
            }
            parent = parent.uastParent
        }

        val versionCheckParent = node.getParentOfType<UIfExpression>(strict = true)
        if (versionCheckParent != null) {
            val conditionText = versionCheckParent.condition.asSourceString()
            if (referencesApiLevelCheck(conditionText)) {
                return true
            }
        }

        return context.isWithinVersionCheckSince(node, ANDROID_P_API_LEVEL)
    }

    private fun referencesApiLevelCheck(conditionText: String): Boolean {
        return conditionText.contains("SDK_INT") ||
            conditionText.contains("VERSION.SDK") ||
            conditionText.contains("isAtLeast") ||
            conditionText.contains("BuildCompat")
    }

    private fun JavaContext.isWithinVersionCheckSince(node: UCallExpression, apiLevel: Int): Boolean {
        return try {
            val evaluator = this.evaluator
            evaluator.getApiLevel(node, null) >= apiLevel
        } catch (e: Exception) {
            false
        }
    }
}