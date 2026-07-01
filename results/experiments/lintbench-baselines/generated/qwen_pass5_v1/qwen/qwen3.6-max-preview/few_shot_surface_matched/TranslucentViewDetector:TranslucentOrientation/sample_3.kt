package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class TranslucentViewDetector : Detector(), XmlScanner, SourceCodeScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("activity")
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf("screenOrientation")
  }

  override fun appliesTo(context: Context, scope: Scope): Boolean {
    return true
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    // Element-level scanning is delegated to attribute visitation for screenOrientation.
    // Additional manifest activity checks can be placed here if required.
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    if (attribute.localName != "screenOrientation") return
    val value = attribute.value
    if (value == "unspecified" || value == "behind") return

    val message = "Specifying a fixed screenOrientation on a translucent activity will throw " +
      "an exception on Android O (API 26) and higher. Ensure this activity does not use " +
      "a translucent theme, or remove the fixed orientation."
    context.report(
      Incident(ISSUE, attribute, context.getValueLocation(attribute), message)
    )
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    val targetSdk = context.project.targetSdkVersion ?: return false
    return targetSdk >= 26
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("setRequestedOrientation")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
      return
    }

    val message = "Calling setRequestedOrientation on a translucent activity will throw " +
      "an exception on Android O (API 26) and higher. Ensure this activity does not use " +
      "a translucent theme, or avoid programmatically locking the orientation."
    context.report(
      Incident(ISSUE, node, context.getLocation(node), message)
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "TranslucentOrientation",
      briefDescription = "Mixing screenOrientation and translucency",
      explanation = """
        Specifying a fixed screen orientation with a translucent theme isn't supported \
        on apps with targetSdkVersion O or greater since there can be another activity \
        visible behind your activity with a conflicting request. Devices running platform \
        version O or greater will throw an exception in your app if this state is detected.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        TranslucentViewDetector::class.java,
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )
  }
}