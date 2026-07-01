package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastCallKind

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.kind != UastCallKind.CONSTRUCTOR_CALL) return
        val method = node.resolve() ?: return
        val qualifiedName = method.containingClass?.qualifiedName ?: return

        if (qualifiedName == "androidx.credentials.CreatePublicKeyCredentialRequest" ||
            qualifiedName == "android.credentials.CreatePublicKeyCredentialRequest") {

            if (context.mainProject.minSdkVersion < 28) {
                if (!context.isWithinVersionCheckConditional(node, 28)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Creating public key credential requires Android 9 (API 28) or higher. " +
                        "Please check for the Android version before calling this method."
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            "PublicKeyCredential",
            "Creating public key credential requires Android 9 or higher",
            "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. " +
            "Please check for the Android version before calling the method.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}