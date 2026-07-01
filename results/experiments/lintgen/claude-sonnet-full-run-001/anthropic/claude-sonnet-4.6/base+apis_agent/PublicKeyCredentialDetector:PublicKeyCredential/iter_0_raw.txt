package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
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
            )
        )

        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        private val TARGET_CONSTRUCTORS = setOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )

        private val TARGET_METHODS = mapOf(
            CREDENTIAL_MANAGER_CLASS to setOf(
                "createCredential",
                "createCredentialAsync",
                "getCredential",
                "getCredentialAsync"
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return TARGET_CONSTRUCTORS.toList()
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isVersionChecked(context, node)) {
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
        return TARGET_METHODS.values.flatten().distinct()
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val targetMethods = TARGET_METHODS[containingClass] ?: return
        if (method.name !in targetMethods) return

        if (!isVersionChecked(context, node)) {
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

    private fun isVersionChecked(context: JavaContext, node: UElement): Boolean {
        // Android 9 is API level 28
        val requiredApi = 28
        return VersionChecks.isWithinVersionCheckConditional(context, node, requiredApi) ||
                VersionChecks.isPrecededByVersionCheckExit(context, node, requiredApi)
    }
}