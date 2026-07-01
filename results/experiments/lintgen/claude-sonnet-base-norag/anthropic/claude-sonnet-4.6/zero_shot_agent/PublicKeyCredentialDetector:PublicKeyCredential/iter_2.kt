package com.android.tools.lint.checks

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
import org.jetbrains.uast.UCallableReferenceExpression

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"

        private val CREDENTIAL_MANAGER_METHOD_NAMES = setOf(
            "createCredential",
            "createCredentialAsync",
            "getCredential",
            "getCredentialAsync"
        )

        private const val P_VERSION_CODE = 28 // Android 9 = API 28

        @JvmField
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

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "createCredential",
            "createCredentialAsync",
            "getCredential",
            "getCredentialAsync"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        if (containingClass != CREDENTIAL_MANAGER_CLASS) return
        if (method.name !in CREDENTIAL_MANAGER_METHOD_NAMES) return

        if (!isPublicKeyCredentialCall(node)) return

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

    private fun isPublicKeyCredentialCall(node: UCallExpression): Boolean {
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
                break
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun containsVersionCheck(conditionText: String): Boolean {
        if (!conditionText.contains("SDK_INT")) {
            return false
        }

        // Pattern: SDK_INT >= <value>
        val sdkIntGtePattern = Regex("""SDK_INT\s*>=\s*(\w+)""")
        val matchGte = sdkIntGtePattern.find(conditionText)
        if (matchGte != null) {
            val value = matchGte.groupValues[1]
            if (isAtLeastApi28(value)) return true
        }

        // Pattern: SDK_INT > <value>
        val sdkIntGtPattern = Regex("""SDK_INT\s*>\s*(\w+)""")
        val matchGt = sdkIntGtPattern.find(conditionText)
        if (matchGt != null) {
            val value = matchGt.groupValues[1]
            val numericValue = value.toIntOrNull()
            if (numericValue != null && numericValue >= P_VERSION_CODE - 1) return true
        }

        // Pattern: <value> <= SDK_INT
        val valueLtePattern = Regex("""(\w+)\s*<=\s*SDK_INT""")
        val matchLte = valueLtePattern.find(conditionText)
        if (matchLte != null) {
            val value = matchLte.groupValues[1]
            if (isAtLeastApi28(value)) return true
        }

        // Pattern: <value> < SDK_INT
        val valueLtPattern = Regex("""(\w+)\s*<\s*SDK_INT""")
        val matchLt = valueLtPattern.find(conditionText)
        if (matchLt != null) {
            val value = matchLt.groupValues[1]
            val numericValue = value.toIntOrNull()
            if (numericValue != null && numericValue >= P_VERSION_CODE - 1) return true
        }

        return false
    }

    private fun isAtLeastApi28(value: String): Boolean {
        val trimmed = value.trim()

        // Check if it's a numeric value
        val numericValue = trimmed.toIntOrNull()
        if (numericValue != null) {
            return numericValue >= P_VERSION_CODE
        }

        // Check for named constants (last segment after dot)
        val apiName = trimmed.substringAfterLast(".")
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
                apiName.toIntOrNull()?.let { it >= P_VERSION_CODE } ?: false
            }
        }
    }
}