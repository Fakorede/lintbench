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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

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

        private val APPLICABLE_CONSTRUCTOR_TYPES = listOf(
            PUBLIC_KEY_CREDENTIAL_CLASS,
            CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS,
            GET_PUBLIC_KEY_CREDENTIAL_OPTION_CLASS,
        )

        private const val SDK_INT_CHECK_PATTERN = "SDK_INT"
        private const val BUILD_VERSION_CODES_P = "P"
        private const val API_LEVEL_28 = 28 // Android 9 (Pie)
    }

    override fun getApplicableConstructorTypes(): List<String> = APPLICABLE_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (isInsideVersionCheck(node)) {
            return
        }

        context.report(
            issue = ISSUE,
            location = context.getLocation(node),
            message = "Credential Manager API supports creating public key credential " +
                "(Passkeys) starting Android 9 or higher. Please check for the Android " +
                "version before calling the method.",
        )
    }

    private fun isInsideVersionCheck(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition.asSourceString()
                if (containsVersionCheck(condition)) {
                    return true
                }
            }
            if (parent is UMethod) {
                break
            }
            parent = parent.uastParent
        }

        // Also check using getParentOfType for UIfExpression
        var ifExpr = node.getParentOfType<UIfExpression>(strict = true)
        while (ifExpr != null) {
            val condition = ifExpr.condition.asSourceString()
            if (containsVersionCheck(condition)) {
                return true
            }
            ifExpr = ifExpr.getParentOfType<UIfExpression>(strict = true)
        }

        return false
    }

    private fun containsVersionCheck(condition: String): Boolean {
        // Check if the condition references SDK_INT and a version >= 28 (Android 9 / P)
        if (!condition.contains(SDK_INT_CHECK_PATTERN)) {
            return false
        }

        // Check for VERSION_CODES.P or higher
        if (condition.contains(BUILD_VERSION_CODES_P)) {
            return true
        }

        // Check for numeric API level >= 28
        val sdkIntPattern = Regex("""SDK_INT\s*[>]=?\s*(\d+)""")
        val match = sdkIntPattern.find(condition)
        if (match != null) {
            val apiLevel = match.groupValues[1].toIntOrNull()
            if (apiLevel != null && apiLevel >= API_LEVEL_28) {
                return true
            }
        }

        val sdkIntPatternReversed = Regex("""(\d+)\s*[<]=?\s*SDK_INT""")
        val matchReversed = sdkIntPatternReversed.find(condition)
        if (matchReversed != null) {
            val apiLevel = matchReversed.groupValues[1].toIntOrNull()
            if (apiLevel != null && apiLevel >= API_LEVEL_28) {
                return true
            }
        }

        return false
    }
}