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

        private val TARGET_CLASSES = setOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )

        private const val CREATE_CREDENTIAL_METHOD = "createCredential"
        private const val GET_CREDENTIAL_METHOD = "getCredential"

        // Android 9 is API level 28
        private const val MIN_REQUIRED_API = 28

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

        private fun isVersionCheckPresent(node: UCallExpression): Boolean {
            var parent: UElement? = node.uastParent
            while (parent != null) {
                if (parent is UIfExpression) {
                    val condition = parent.condition.asSourceString()
                    if (containsApiVersionCheck(condition)) {
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

        private fun containsApiVersionCheck(condition: String): Boolean {
            // Check for SDK_INT comparisons covering API 28+
            // Patterns like: Build.VERSION.SDK_INT >= 28
            //                Build.VERSION.SDK_INT > 27
            //                SDK_INT >= Build.VERSION_CODES.P
            val sdkIntPattern = Regex(
                """SDK_INT\s*>=\s*(\d+)|SDK_INT\s*>\s*(\d+)|(\d+)\s*<=\s*SDK_INT|(\d+)\s*<\s*SDK_INT"""
            )
            val match = sdkIntPattern.find(condition)
            if (match != null) {
                val value = (match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()
                    ?: match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()?.plus(1)
                    ?: match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
                    ?: match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()?.plus(1))
                if (value != null && value >= MIN_REQUIRED_API) {
                    return true
                }
            }

            // Check for named version code references (P = API 28)
            if (condition.contains("SDK_INT") &&
                (condition.contains("VERSION_CODES.P") ||
                    condition.contains("VERSION_CODES.Q") ||
                    condition.contains("VERSION_CODES.R") ||
                    condition.contains("VERSION_CODES.S") ||
                    condition.contains("VERSION_CODES.TIRAMISU") ||
                    condition.contains("VERSION_CODES.UPSIDE_DOWN_CAKE"))
            ) {
                return true
            }

            return false
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val containingClass = constructor.containingClass?.qualifiedName ?: return
        if (containingClass in TARGET_CLASSES) {
            if (!isVersionCheckPresent(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Credential Manager API supports creating public key credential " +
                        "(Passkeys) starting Android 9 or higher. Please check for the " +
                        "Android version before calling the method."
                )
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(CREATE_CREDENTIAL_METHOD, GET_CREDENTIAL_METHOD)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass != CREDENTIAL_MANAGER_CLASS) return

        // Check if the arguments include a CreatePublicKeyCredentialRequest or PublicKeyCredential
        val arguments = node.valueArguments
        for (arg in arguments) {
            val argType = arg.getExpressionType()?.canonicalText ?: continue
            if (argType in TARGET_CLASSES) {
                if (!isVersionCheckPresent(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Credential Manager API supports creating public key credential " +
                            "(Passkeys) starting Android 9 or higher. Please check for the " +
                            "Android version before calling the method."
                    )
                }
                return
            }
        }
    }
}