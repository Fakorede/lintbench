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
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  private var hasQueries = false
  private var hasQueryAllPackages = false

  override fun getApplicableElements(): Collection<String>? {
    return listOf("queries", "uses-permission")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName == "queries") {
      hasQueries = true
    } else if (element.tagName == "uses-permission") {
      val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
      if (name == "android.permission.QUERY_ALL_PACKAGES") {
        hasQueryAllPackages = true
      }
    }
  }

  override fun getApplicableMethodNames(): List<String>? {
    return listOf("getInstalledPackages", "getInstalledApplications")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      val message = "Using APIs affected by query permissions; " +
        "PackageManager#getInstalledPackages and PackageManager#getInstalledApplications " +
        "will no longer return information about all installed apps. " +
        "To query specific apps or types of apps, use methods like " +
        "PackageManager#getPackageInfo or PackageManager#queryIntentActivities."
      val incident = Incident(ISSUE, node, context.getLocation(node), message)
      context.report(incident)
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap) {
    if (context.project.targetSdkVersion.apiLevel < 30) {
      return
    }
    if (hasQueries || hasQueryAllPackages) {
      return
    }
    super.filterIncident(context, incident, map)
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "QueryPermissionsNeeded",
      briefDescription = "Using APIs affected by query permissions",
      explanation = "Apps that target Android 11 cannot query or interact with other installed apps " +
        "by default. If you need to query or interact with other installed apps, you may need " +
        "to add a `<queries>` declaration in your manifest.\n\n" +
        "As a corollary, the methods `PackageManager#getInstalledPackages` and " +
        "`PackageManager#getInstalledApplications` will no longer return information about all " +
        "installed apps. To query specific apps or types of apps, you can use methods like " +
        "`PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        Scope.MANIFEST_AND_JAVA_FILES
      )
    )
  }
}