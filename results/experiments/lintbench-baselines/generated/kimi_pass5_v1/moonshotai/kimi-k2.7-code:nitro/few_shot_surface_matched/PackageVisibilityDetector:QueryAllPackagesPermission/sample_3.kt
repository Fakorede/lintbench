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

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
private const val QUERY_ALL_PACKAGES_MIN_TARGET_SDK = 30

class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("uses-permission")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(ANDROID_URI, "name") ?: return
    if (attr.value != QUERY_ALL_PACKAGES_PERMISSION) return

    context.report(
      Incident(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "Using the QUERY_ALL_PACKAGES permission is rarely necessary. " +
          "If you need to query or interact with other installed apps, " +
          "declare the appropriate `<queries>` entries in your manifest instead. " +
          "See https://g.co/dev/packagevisibility."
      )
    )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      "checkSelfPermission",
      "requestPermissions",
      "shouldShowRequestPermissionRationale"
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arg = node.valueArguments.firstOrNull() ?: return
    val value = arg.evaluate() as? String ?: return
    if (value != QUERY_ALL_PACKAGES_PERMISSION) return

    val location = context.getLocation(arg)
    val message =
      "Using the QUERY_ALL_PACKAGES permission is rarely necessary. " +
        "If you need to query or interact with other installed apps, " +
        "declare the appropriate `<queries>` entries in your manifest instead. " +
        "See https://g.co/dev/packagevisibility."

    context.report(Incident(ISSUE, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    val targetSdk = context.project.buildTargetSdk ?: return true
    return targetSdk.featureLevel >= QUERY_ALL_PACKAGES_MIN_TARGET_SDK
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "QueryAllPackagesPermission",
      briefDescription = "Using QUERY_ALL_PACKAGES permission",
      explanation =
        """
                If your app needs to query or interact with other installed apps, you should declare
                the appropriate `<queries>` entries in your manifest. Using the QUERY_ALL_PACKAGES
                permission to see all installed apps is rarely necessary, and most apps on Google Play
                are not allowed to have this permission.

                For more information, see https://g.co/dev/packagevisibility.
            """,
      category = Category.COMPLIANCE,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        enumSetOf(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
      )
    )
  }
}