package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> = listOf(USES_PERMISSION)

  override fun visitElement(context: XmlContext, element: Element) {
    val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    if (QUERY_ALL_PACKAGES != attr.value) return

    val message =
      "Using the QUERY_ALL_PACKAGES permission. Prefer a `<queries>` declaration instead; " +
      "this permission is rarely necessary and most apps on Google Play are not allowed to use it."
    val location = context.getLocation(attr)
    context.report(
      QUERY_ALL_PACKAGES_PERMISSION,
      attr,
      location,
      message
    )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf(GET_INSTALLED_APPLICATIONS, GET_INSTALLED_PACKAGES)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER)) return

    val message =
      "This method queries all installed apps and requires QUERY_ALL_PACKAGES permission on " +
      "Android 11+ (API 30+). Prefer a `<queries>` declaration in your manifest unless " +
      "QUERY_ALL_PACKAGES is truly necessary."
    val location = context.getLocation(node)
    context.report(
      QUERY_ALL_PACKAGES_PERMISSION,
      node,
      location,
      message
    )
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap?): Boolean {
    return context.mainProject.targetSdk >= API_30
  }

  companion object {
    private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val USES_PERMISSION = "uses-permission"
    private const val ATTR_NAME = "name"
    private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
    private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
    private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
    private const val API_30 = 30

    @JvmField
    val QUERY_ALL_PACKAGES_PERMISSION = Issue.create(
      id = "QueryAllPackagesPermission",
      briefDescription = "Using the QUERY_ALL_PACKAGES permission",
      explanation =
        "If you need to query or interact with other installed apps, you should be using a " +
        "`<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in " +
        "order to see all installed apps is rarely necessary, and most apps on Google Play are " +
        "not allowed to have this permission.\n\n" +
        "See https://g.co/dev/packagevisibility for more details.",
      category = Category.COMPLIANCE,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
      ),
      androidSpecific = true
    )
  }
}