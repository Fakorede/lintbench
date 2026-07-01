package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. \
                Please check for the Android version before calling the method.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.CreatePublicKeyCredentialRequest",
            "androidx.credentials.CreatePublicKeyCredentialRequest.Builder"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk >= 28) {
            return
        }

        if (!VersionChecks.isWithinVersionConditionalCheck(context, node, 28)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Creating public key credential requires Android 9 (API 28) or higher, or a surrounding SDK version check"
            )
        }
    }
}