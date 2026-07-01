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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryExpressionOperator
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val MIN_API_VERSION: Int = 28
    private const val REQUEST_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

    @JvmField
    val PUBLIC_KEY_CREDENTIAL =
      Issue.create(
        id = "PublicKeyCredential",
        briefDescription = "CreatePublicKeyCredentialRequest requires Android 9 or higher",
        explanation =
          """
                The Credential Manager API supports creating public key credentials (Passkeys)
                starting with Android 9 (API 28). Calls to `CreatePublicKeyCredentialRequest`
                should be guarded with a runtime version check such as
                `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> = listOf(REQUEST_CLASS)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    if (isWithinVersionGuard(node)) {
      return
    }

    val message =
      "CreatePublicKeyCredentialRequest is only supported on Android 9 (API 28) or higher. " +
        "Check Build.VERSION.SDK_INT >= Build.VERSION_CODES.P before constructing it."
    val location = context.getLocation(node)
    context.report(Incident(PUBLIC_KEY_CREDENTIAL, node, location, message))
  }

  private fun isWithinVersionGuard(node: UCallExpression): Boolean {
    var current: UElement? = node
    while (current != null) {
      val ifExpression = current.getParentOfType(UIfExpression::class.java, false) ?: break
      if (isSdkVersionCheck(ifExpression.condition)) {
        return true
      }
      current = ifExpression.uastParent
    }
    return false
  }

  private fun isSdkVersionCheck(condition: UExpression?): Boolean {
    val binary = condition as? UBinaryExpression ?: return false
    val operator = binary.operator
    if (operator != UastBinaryExpressionOperator.GREATER &&
      operator != UastBinaryExpressionOperator.GREATER_OR_EQUALS &&
      operator != UastBinaryExpressionOperator.EQUALS
    ) {
      return false
    }

    return (isSdkIntReference(binary.leftOperand) && isApi28OrHigher(binary.rightOperand)) ||
      (isSdkIntReference(binary.rightOperand) && isApi28OrHigher(binary.leftOperand))
  }

  private fun isSdkIntReference(expression: UExpression?): Boolean {
    val reference = expression as? UReferenceExpression ?: return false
    return reference.resolvedName == "SDK_INT"
  }

  private fun isApi28OrHigher(expression: UExpression?): Boolean {
    if (expression is ULiteralExpression) {
      val intValue = (expression.value as? Number)?.toInt() ?: return false
      return intValue >= MIN_API_VERSION
    }

    val reference = expression as? UReferenceExpression ?: return false
    return reference.resolvedName == "P"
  }
}