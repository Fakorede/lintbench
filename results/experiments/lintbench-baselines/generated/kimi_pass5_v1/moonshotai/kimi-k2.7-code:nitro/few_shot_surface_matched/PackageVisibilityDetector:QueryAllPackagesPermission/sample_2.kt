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

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements() = listOf("uses-permission")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(ANDROID_URI, "name") ?: return
    if (attr.value != QUERY_ALL_PACKAGES_PERMISSION_NAME) return

    val message =
      "Using the QUERY_ALL_PACKAGES permission is rarely necessary. " +
      "Most apps on Google Play are not allowed to have this permission. " +
      "Use <queries> declarations in the manifest instead."

    context.report(
      Incident(
        QUERY_ALL_PACKAGES_PERMISSION,
        attr,
        context.getValueLocation(attr),
        message
      )
    )
  }

  override fun getApplicableMethodNames() = listOf(
    "getInstalledApplications",
    "getInstalledPackages",
    "queryIntentActivities",
    "queryBroadcastReceivers",
    "queryContentProviders",
    "queryIntentServices"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      return
    }

    val message =
      "If you need to query or interact with other installed apps, use a " +
      "<queries> declaration in the manifest instead of the QUERY_ALL_PACKAGES permission."

    context.report(
      Incident(
        QUERY_ALL_PACKAGES_PERMISSION,
        node,
        context.getLocation(node),
        message
      )
    )
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return context.mainProject.targetSdkVersion.featureLevel >= 30
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val QUERY_ALL_PACKAGES_PERMISSION_NAME = "android.permission.QUERY_ALL_PACKAGES"

    @JvmField
    val QUERY_ALL_PACKAGES_PERMISSION = Issue.create(
      id = "QueryAllPackagesPermission",
      briefDescription = "Using QUERY_ALL_PACKAGES permission",
      explanation =
        "If you need to query or interact with other installed apps, you should use a " +
        "`<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission " +
        "in order to see all installed apps is rarely necessary, and most apps on Google Play " +
        "are not allowed to have this permission. See https://g.co/dev/packagevisibility.",
      category = Category.COMPLIANCE,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE
      ),
      androidSpecific = true
    )
  }
}