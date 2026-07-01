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
import com.android.tools.lint.detector.api.VersionChecks
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val MIN_API: Int = 28
    private const val REQUEST_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "PublicKeyCredential",
        briefDescription = "Creating public key credentials requires Android 9 or higher",
        explanation =
          """
                Credential Manager supports creating public key credentials (Passkeys) starting with Android 9 \
                (API level 28). Constructing `CreatePublicKeyCredentialRequest` on older devices can cause runtime \
                errors. Wrap this call in a version check such as \
                `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { ... }`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes() = listOf(REQUEST_CLASS)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    if (VersionChecks.isPrecededByVersionCheck(node, context.evaluator, MIN_API)) {
      return
    }

    val location = context.getLocation(node)
    val message =
      "Creating public key credentials requires Android 9 (API 28) or higher. Add a version check before this call."
    context.report(Incident(ISSUE, node, location, message))
  }
}