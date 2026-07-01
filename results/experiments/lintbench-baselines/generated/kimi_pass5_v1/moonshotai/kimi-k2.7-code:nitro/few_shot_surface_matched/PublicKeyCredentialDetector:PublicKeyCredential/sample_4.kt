package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

  companion object {

    @JvmField
    val PUBLIC_KEY_CREDENTIAL =
      Issue.create(
        id = "PublicKeyCredential",
        briefDescription = "Check Android version before creating a public key credential",
        explanation =
          """
                Credential Manager supports creating public key credentials (Passkeys) only on Android 9 (API level 28) and higher. You should check the Android version before constructing or using CreatePublicKeyCredentialRequest.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST =
      "androidx.credentials.CreatePublicKeyCredentialRequest"
  }

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    val message =
      "Creating public key credentials (Passkeys) requires Android 9 (API 28) or higher. Add a version check before using CreatePublicKeyCredentialRequest."
    context.report(
      Incident(
        PUBLIC_KEY_CREDENTIAL,
        node,
        context.getLocation(node),
        message,
      )
    )
  }
}