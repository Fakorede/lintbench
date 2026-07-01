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
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PUBLIC_KEY_CREDENTIAL_CREATE_REQUEST =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL =
            "androidx.credentials.PublicKeyCredential"
        private const val ANDROID_9_API_LEVEL = 28

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation =
                """
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
        PUBLIC_KEY_CREDENTIAL_CREATE_REQUEST,
        PUBLIC_KEY_CREDENTIAL
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isGuardedByVersionCheck(node)) {
            val className = constructor.containingClass?.qualifiedName ?: return
            val simpleName = constructor.containingClass?.name ?: className
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Credential Manager API supports creating `$simpleName` (Passkeys) starting " +
                    "Android 9 (API level $ANDROID_9_API_LEVEL) or higher. Please check for " +
                    "the Android version before calling this constructor."
            )
        }
    }

    private fun isGuardedByVersionCheck(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                val conditionText = condition.asSourceString()
                if (containsVersionCheck(conditionText)) {
                    return true
                }
            }
            if (current is UMethod) {
                break
            }
            current = current.uastParent
        }
        return false
    }

    private fun containsVersionCheck(conditionText: String): Boolean {
        // Check for Build.VERSION.SDK_INT comparisons against API 28 or higher
        if (!conditionText.contains("SDK_INT")) {
            return false
        }
        // Look for patterns like SDK_INT >= 28, SDK_INT > 27, etc.
        val sdkIntPattern = Regex(
            """SDK_INT\s*(>=|>)\s*(\d+)"""
        )
        val matchGe = sdkIntPattern.find(conditionText)
        if (matchGe != null) {
            val operator = matchGe.groupValues[1]
            val value = matchGe.groupValues[2].toIntOrNull() ?: return false
            return when (operator) {
                ">=" -> value >= ANDROID_9_API_LEVEL
                ">" -> value >= ANDROID_9_API_LEVEL - 1
                else -> false
            }
        }
        // Look for patterns like 28 <= SDK_INT, 27 < SDK_INT
        val reversedPattern = Regex(
            """(\d+)\s*(<=|<)\s*SDK_INT"""
        )
        val matchLe = reversedPattern.find(conditionText)
        if (matchLe != null) {
            val value = matchLe.groupValues[1].toIntOrNull() ?: return false
            val operator = matchLe.groupValues[2]
            return when (operator) {
                "<=" -> value <= ANDROID_9_API_LEVEL
                "<" -> value < ANDROID_9_API_LEVEL
                else -> false
            }
        }
        return false
    }
}