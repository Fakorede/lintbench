package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_QUERIES
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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

private const val ANDROID_11_API_LEVEL = 30

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_QUERIES)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // We scan <queries> declarations so the detector participates in manifest analysis.
    // The actual warning is reported on affected PackageManager API calls.
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getInstalledPackages", "getInstalledApplications")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val message =
      "Apps that target Android 11 (API 30) or higher cannot query or interact with " +
      "other installed apps by default. `PackageManager#getInstalledPackages` and " +
      "`PackageManager#getInstalledApplications` will no longer return information about " +
      "all installed apps. Add a `<queries>` declaration to your manifest or use " +
      "`PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities` instead. " +
      "See https://g.co/dev/packagevisibility."

    context.report(
      Incident(ISSUE, node, context.getLocation(node), message),
      targetSdkAtLeast(ANDROID_11_API_LEVEL)
    )
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return true
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "QueryPermissionsNeeded",
      briefDescription = "Using APIs affected by query permissions",
      explanation = """
        Apps that target Android 11 (API 30) or higher cannot query or interact with
        other installed apps by default. If you need to query or interact with other
        installed apps, you may need to add a `<queries>` declaration in your manifest.

        As a corollary, `PackageManager#getInstalledPackages` and
        `PackageManager#getInstalledApplications` will no longer return information about
        all installed apps. To query specific apps or types of apps, you can use methods
        like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

        See https://g.co/dev/packagevisibility.
        """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.MANIFEST_SCOPE
      ),
      androidSpecific = true
    )
  }
}