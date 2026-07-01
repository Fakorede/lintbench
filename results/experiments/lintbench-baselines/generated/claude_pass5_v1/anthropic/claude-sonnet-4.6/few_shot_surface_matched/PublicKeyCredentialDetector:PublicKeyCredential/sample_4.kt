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

        private const val ANDROID_9_API_LEVEL = 28

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
        GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isGuardedByVersionCheck(node)) {
            val message =
                "Credential Manager API supports creating public key credential (Passkeys) " +
                    "starting Android 9 (API level $ANDROID_9_API_LEVEL) or higher. " +
                    "Please check for the Android version before calling this constructor."
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        }
    }

    private fun isGuardedByVersionCheck(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition
                val conditionText = condition.asSourceString()
                if (containsVersionCheck(conditionText)) {
                    return true
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun containsVersionCheck(conditionText: String): Boolean {
        // Check for Build.VERSION.SDK_INT comparisons against API 28 (Android 9) or higher
        if (!conditionText.contains("SDK_INT")) {
            return false
        }
        // Look for patterns like SDK_INT >= 28, SDK_INT > 27, etc.
        val sdkIntPattern = Regex("""SDK_INT\s*>=\s*(\d+)""")
        val sdkIntPatternGt = Regex("""SDK_INT\s*>\s*(\d+)""")
        val sdkIntPatternLt = Regex("""(\d+)\s*<=\s*SDK_INT""")
        val sdkIntPatternLte = Regex("""(\d+)\s*<\s*SDK_INT""")

        sdkIntPattern.find(conditionText)?.let { match ->
            val version = match.groupValues[1].toIntOrNull()
            if (version != null && version >= ANDROID_9_API_LEVEL) return true
        }

        sdkIntPatternGt.find(conditionText)?.let { match ->
            val version = match.groupValues[1].toIntOrNull()
            if (version != null && version >= ANDROID_9_API_LEVEL - 1) return true
        }

        sdkIntPatternLt.find(conditionText)?.let { match ->
            val version = match.groupValues[1].toIntOrNull()
            if (version != null && version >= ANDROID_9_API_LEVEL) return true
        }

        sdkIntPatternLte.find(conditionText)?.let { match ->
            val version = match.groupValues[1].toIntOrNull()
            if (version != null && version >= ANDROID_9_API_LEVEL - 1) return true
        }

        // Check for named constants like Build.VERSION_CODES.P
        if (conditionText.contains("VERSION_CODES.P") ||
            conditionText.contains("VERSION_CODES.Q") ||
            conditionText.contains("VERSION_CODES.R") ||
            conditionText.contains("VERSION_CODES.S") ||
            conditionText.contains("VERSION_CODES.TIRAMISU") ||
            conditionText.contains("VERSION_CODES.UPSIDE_DOWN_CAKE")
        ) {
            return true
        }

        return false
    }
}