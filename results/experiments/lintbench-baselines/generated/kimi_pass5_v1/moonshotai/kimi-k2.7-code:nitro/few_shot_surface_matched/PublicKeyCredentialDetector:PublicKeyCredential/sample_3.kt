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
        briefDescription = "Creating public key credentials requires Android 9+",
        explanation =
          """
                Credential Manager API supports creating public key credential (Passkeys) starting
                Android 9 (API 28) or higher. Please check for the Android version before calling
                the constructor.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("androidx.credentials.CreatePublicKeyCredentialRequest")
  }

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val location = context.getLocation(node)
    val message =
      "CreatePublicKeyCredentialRequest is only supported on Android 9 (API 28) or higher. Check the Android version before constructing this request."
    context.report(Incident(PUBLIC_KEY_CREDENTIAL, node, location, message))
  }
}