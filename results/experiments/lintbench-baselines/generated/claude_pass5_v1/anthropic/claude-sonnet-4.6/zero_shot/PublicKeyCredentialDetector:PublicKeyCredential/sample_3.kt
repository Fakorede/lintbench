package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val CREATE_CREDENTIAL_METHOD = "createCredential"
        private const val CREDENTIAL_MANAGER_CLASS = "androidx.credentials.CredentialManager"

        // Android 9 = API 28
        private const val MIN_REQUIRED_API = 28

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

        private val SDK_INT_CHECKS = setOf(
            "android.os.Build.VERSION.SDK_INT",
            "SDK_INT"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(CREATE_CREDENTIAL_METHOD, "createPublicKeyCredential")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        // Check if this is a createCredential call on CredentialManager
        val isCreateCredential = CREATE_CREDENTIAL_METHOD == method.name &&
                isCredentialManagerClass(containingClass)

        // Check if this is a direct createPublicKeyCredential call
        val isCreatePublicKeyCredential = method.name == "createPublicKeyCredential"

        if (!isCreateCredential && !isCreatePublicKeyCredential) {
            return
        }

        // For createCredential, check if a CreatePublicKeyCredentialRequest is passed
        if (isCreateCredential) {
            val hasPublicKeyArg = node.valueArguments.any { arg ->
                val type = arg.getExpressionType()?.canonicalText ?: ""
                type == PUBLIC_KEY_CREDENTIAL_CLASS ||
                        type.contains("PublicKeyCredential")
            }
            if (!hasPublicKeyArg) {
                return
            }
        }

        // Check if there's a version guard in the surrounding code
        if (isGuardedByVersionCheck(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Credential Manager API supports creating public key credential (Passkeys) " +
                    "starting Android 9 or higher. Please check for the Android version " +
                    "before calling the method."
        )
    }

    private fun isCredentialManagerClass(className: String): Boolean {
        return className == CREDENTIAL_MANAGER_CLASS ||
                className.contains("CredentialManager")
    }

    private fun isGuardedByVersionCheck(node: UCallExpression): Boolean {
        // Walk up the UAST tree looking for version checks
        var parent: UElement? = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition.asSourceString()
                if (containsVersionCheck(condition)) {
                    return true
                }
            }
            if (parent is UMethod) {
                // Check method-level annotations or method body for version checks
                break
            }
            parent = parent.uastParent
        }

        // Also check enclosing method for version checks
        val enclosingMethod = node.getParentOfType<UMethod>(UMethod::class.java) ?: return false
        val methodSource = enclosingMethod.asSourceString()
        return containsVersionCheck(methodSource)
    }

    private fun containsVersionCheck(source: String): Boolean {
        return (source.contains("SDK_INT") || source.contains("Build.VERSION")) &&
                (source.contains("28") ||
                        source.contains("Build.VERSION_CODES.P") ||
                        source.contains("VERSION_CODES.P") ||
                        source.contains("29") ||
                        source.contains("30") ||
                        source.contains("31") ||
                        source.contains("32") ||
                        source.contains("33") ||
                        source.contains("34") ||
                        source.contains("O_MR1") ||
                        source.contains("Q") ||
                        source.contains("R") ||
                        source.contains("S") ||
                        source.contains("T") ||
                        source.contains("U"))
    }
}