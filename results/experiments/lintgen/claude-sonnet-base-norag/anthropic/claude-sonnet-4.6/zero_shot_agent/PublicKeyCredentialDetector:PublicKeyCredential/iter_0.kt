package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"

        private val CREATE_METHOD_NAMES = setOf(
            "createCredential",
            "createCredentialAsync",
            "getCredential",
            "getCredentialAsync"
        )

        private const val BUILD_VERSION_CLASS = "android.os.Build.VERSION"
        private const val SDK_INT = "SDK_INT"
        private const val BUILD_VERSION_CODES_CLASS = "android.os.Build.VERSION_CODES"
        private const val P_VERSION_CODE = 28 // Android 9 = API 28

        val ISSUE: Issue = Issue.create(
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
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "CreatePublicKeyCredentialRequest",
            "createCredential",
            "createCredentialAsync",
            "getCredential",
            "getCredentialAsync",
            "PublicKeyCredential"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isRelevantCall = when {
            containingClass == CREATE_PUBLIC_KEY_CREDENTIAL_CLASS -> true
            containingClass == CREDENTIAL_MANAGER_CLASS &&
                    method.name in CREATE_METHOD_NAMES -> {
                // Check if the call involves PublicKeyCredential types
                isPublicKeyCredentialCall(context, node)
            }
            containingClass == PUBLIC_KEY_CREDENTIAL_CLASS -> true
            else -> false
        }

        if (!isRelevantCall) return

        if (!isInsideVersionCheck(node)) {
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

    private fun isPublicKeyCredentialCall(context: JavaContext, node: UCallExpression): Boolean {
        val valueArguments = node.valueArguments
        for (arg in valueArguments) {
            val type = arg.getExpressionType()
            val canonicalText = type?.canonicalText ?: continue
            if (canonicalText.contains("PublicKeyCredential") ||
                canonicalText.contains("CreatePublicKeyCredential")
            ) {
                return true
            }
        }
        return false
    }

    private fun isInsideVersionCheck(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition
                val conditionText = condition.asSourceString()
                if (containsVersionCheck(conditionText)) {
                    return true
                }
            }
            if (parent is UMethod) {
                // Check if the method itself has a version check annotation or
                // if the method name suggests it's version-gated
                val methodName = parent.name
                if (methodName.contains("Api28", ignoreCase = true) ||
                    methodName.contains("Api9", ignoreCase = true) ||
                    methodName.contains("Passkey", ignoreCase = true)
                ) {
                    // Heuristic: method name suggests version gating
                    // We still want to report unless there's an explicit check
                }
                break
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun containsVersionCheck(conditionText: String): Boolean {
        // Check for patterns like:
        // Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        // Build.VERSION.SDK_INT >= 28
        // SDK_INT >= 28
        val sdkIntPattern = Regex("""SDK_INT\s*[>]=\s*(\d+|[A-Z_]+\.P\b|VERSION_CODES\.P\b|[A-Z_]*\bP\b)""")
        val sdkIntPatternLt = Regex("""(\d+|[A-Z_]+\.P\b|VERSION_CODES\.P\b|[A-Z_]*\bP\b)\s*[<]=\s*SDK_INT""")

        if (sdkIntPattern.containsMatchIn(conditionText)) {
            val match = sdkIntPattern.find(conditionText)
            val value = match?.groupValues?.get(1) ?: return false
            return isAtLeastApi28(value)
        }

        if (sdkIntPatternLt.containsMatchIn(conditionText)) {
            val match = sdkIntPatternLt.find(conditionText)
            val value = match?.groupValues?.get(1) ?: return false
            return isAtLeastApi28(value)
        }

        // Check for VERSION_COMPAT or other patterns
        if (conditionText.contains("SDK_INT") &&
            (conditionText.contains(">") || conditionText.contains(">="))
        ) {
            // Try to extract numeric value
            val numericPattern = Regex("""SDK_INT\s*[>]=?\s*(\d+)""")
            val numericMatch = numericPattern.find(conditionText)
            if (numericMatch != null) {
                val apiLevel = numericMatch.groupValues[1].toIntOrNull() ?: return false
                return apiLevel >= P_VERSION_CODE
            }

            // Check for named constants
            if (conditionText.contains(".P") || conditionText.contains("VERSION_CODES.P")) {
                return true
            }

            // Check for higher API levels by name
            val higherApis = listOf("Q", "R", "S", "T", "U", "V")
            for (api in higherApis) {
                if (conditionText.contains("VERSION_CODES.$api") ||
                    conditionText.contains(".$api") && conditionText.contains("VERSION_CODES")
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun isAtLeastApi28(value: String): Boolean {
        // Check if it's a numeric value
        val numericValue = value.trim().toIntOrNull()
        if (numericValue != null) {
            return numericValue >= P_VERSION_CODE
        }

        // Check for named constants
        val apiName = value.trim().substringAfterLast(".")
        return when (apiName) {
            "P" -> true // API 28
            "Q" -> true // API 29
            "R" -> true // API 30
            "S" -> true // API 31
            "S_V2" -> true // API 32
            "TIRAMISU" -> true // API 33
            "UPSIDE_DOWN_CAKE" -> true // API 34
            "VANILLA_ICE_CREAM" -> true // API 35
            else -> {
                // Try to parse as number if it's something like "28"
                apiName.toIntOrNull()?.let { it >= P_VERSION_CODE } ?: false
            }
        }
    }
}