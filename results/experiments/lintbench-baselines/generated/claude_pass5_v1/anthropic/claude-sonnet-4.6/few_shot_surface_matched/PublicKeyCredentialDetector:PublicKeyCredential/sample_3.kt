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
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastBinaryOperator

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
        if (isGuardedByVersionCheck(node)) {
            return
        }

        val message = "Credential Manager API supports creating public key credential " +
            "(Passkeys) starting Android 9 or higher. Please check for the Android " +
            "version before calling the method."

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }

    private fun isGuardedByVersionCheck(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition
                if (conditionChecksMinSdkVersion(condition)) {
                    return true
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun conditionChecksMinSdkVersion(condition: org.jetbrains.uast.UExpression): Boolean {
        val text = condition.asSourceString()
        if (containsVersionCheck(text)) {
            return true
        }
        if (condition is UParenthesizedExpression) {
            return conditionChecksMinSdkVersion(condition.expression)
        }
        if (condition is UPolyadicExpression) {
            if (condition.operator == UastBinaryOperator.LOGICAL_AND ||
                condition.operator == UastBinaryOperator.LOGICAL_OR
            ) {
                for (operand in condition.operands) {
                    if (conditionChecksMinSdkVersion(operand)) {
                        return true
                    }
                }
            }
        }
        if (condition is UQualifiedReferenceExpression) {
            return containsVersionCheck(condition.asSourceString())
        }
        return false
    }

    private fun containsVersionCheck(text: String): Boolean {
        val sdkIntPattern = Regex("""Build\.VERSION\.SDK_INT\s*(>=|>)\s*(\d+)""")
        val sdkIntPatternReversed = Regex("""(\d+)\s*(<=|<)\s*Build\.VERSION\.SDK_INT""")

        val matchForward = sdkIntPattern.find(text)
        if (matchForward != null) {
            val operator = matchForward.groupValues[1]
            val version = matchForward.groupValues[2].toIntOrNull() ?: return false
            return when (operator) {
                ">=" -> version >= ANDROID_P_API_LEVEL
                ">" -> version >= ANDROID_P_API_LEVEL - 1
                else -> false
            }
        }

        val matchReverse = sdkIntPatternReversed.find(text)
        if (matchReverse != null) {
            val version = matchReverse.groupValues[1].toIntOrNull() ?: return false
            val operator = matchReverse.groupValues[2]
            return when (operator) {
                "<=" -> version >= ANDROID_P_API_LEVEL
                "<" -> version >= ANDROID_P_API_LEVEL - 1
                else -> false
            }
        }

        if (text.contains("BuildCompat.isAtLeastP") ||
            text.contains("BuildCompat.isAtLeastQ") ||
            text.contains("BuildCompat.isAtLeastR") ||
            text.contains("BuildCompat.isAtLeastS") ||
            text.contains("BuildCompat.isAtLeastT") ||
            text.contains("BuildCompat.isAtLeastU")
        ) {
            return true
        }

        return false
    }
}