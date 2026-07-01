package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {

  private val methodCalls = mutableListOf<Location>()

  override fun getApplicableElements(): Collection<String> {
    return listOf("uses-permission")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
    if (name == "android.permission.QUERY_ALL_PACKAGES") {
      val incident = Incident(
        ISSUE,
        element,
        context.getNameLocation(element),
        "Using the `QUERY_ALL_PACKAGES` permission, which is rarely allowed on Google Play"
      )
      context.report(incident)
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      "getInstalledPackages",
      "getInstalledApplications",
      "queryIntentActivities",
      "queryIntentServices",
      "queryBroadcastReceivers",
      "queryContentProviders"
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      methodCalls.add(context.getLocation(node))
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (incident.issue == ISSUE) {
      val targetSdk = context.project.targetSdk
      if (targetSdk < 30) {
        return false
      }
      for (location in methodCalls) {
        incident.addSecondary(location, "Querying package visibility here")
      }
    }
    return true
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "QueryAllPackagesPermission",
      briefDescription = "Using the QUERY_ALL_PACKAGES permission",
      explanation = "If you need to query or interact with other installed apps, you should be using a " +
        "<queries> declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in " +
        "order to see all installed apps is rarely necessary, and most apps on Google Play are " +
        "not allowed to have this permission.",
      category = Category.COMPLIANCE,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        Scope.MANIFEST_AND_JAVA_SCOPE
      )
    )
  }
}