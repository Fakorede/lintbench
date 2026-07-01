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
import org.w3c.dom.Element
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("manifest")
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // Manifest scanning hook for package visibility declarations.
    // In a full multi-phase implementation, this would track <queries> or
    // QUERY_ALL_PACKAGES to conditionally suppress warnings in filterIncident.
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getInstalledPackages", "getInstalledApplications")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
      return
    }

    val message = "Using `${method.name}` may not return all apps on Android 11+ due to package visibility changes. " +
      "Consider adding a `<queries>` declaration in your manifest or using alternative methods like " +
      "`getPackageInfo` or `queryIntentActivities`."

    context.report(
      Incident(QUERY_PERMISSIONS_NEEDED, node, context.getLocation(node), message)
    )
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return context.project.targetSdk >= 30
  }

  companion object {
    @JvmField
    val QUERY_PERMISSIONS_NEEDED = Issue.create(
      id = "QueryPermissionsNeeded",
      briefDescription = "Using APIs affected by query permissions",
      explanation = """
        Apps that target Android 11 cannot query or interact with other installed apps by default. \
        If you need to query or interact with other installed apps, you may need to add a `<queries>` \
        declaration in your manifest.

        As a corollary, the methods `PackageManager#getInstalledPackages` and \
        `PackageManager#getInstalledApplications` will no longer return information about all \
        installed apps. To query specific apps or types of apps, you can use methods like \
        `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
      )
    )
  }
}