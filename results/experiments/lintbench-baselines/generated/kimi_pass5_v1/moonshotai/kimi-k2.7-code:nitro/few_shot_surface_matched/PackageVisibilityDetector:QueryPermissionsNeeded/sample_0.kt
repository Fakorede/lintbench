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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

private const val ANDROID_11_API_VERSION: Int = 30

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  private var hasQueriesElement = false

  override fun getApplicableElements(): Collection<String> = listOf("queries")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    hasQueriesElement = true
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "getInstalledPackages",
    "getInstalledApplications"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      return
    }

    val message = buildString {
      append("PackageManager#")
      append(method.name)
      append(" will not return all installed apps when targeting Android 11 (API ")
      append(ANDROID_11_API_VERSION)
      append("+); add a queries declaration to the manifest or use a more targeted API")
    }

    val incident = Incident(
      QUERY_PERMISSIONS_NEEDED,
      node,
      context.getLocation(node),
      message
    )
    context.report(incident, targetSdkAtLeast(ANDROID_11_API_VERSION))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return !hasQueriesElement
  }

  companion object {
    @JvmField
    val QUERY_PERMISSIONS_NEEDED = Issue.create(
      id = "QueryPermissionsNeeded",
      briefDescription = "Using APIs affected by query permissions",
      explanation = """
        Apps that target Android 11 cannot query or interact with other installed apps by default.
        If you need to query or interact with other installed apps, you may need to add a `<queries>`
        declaration in your manifest.

        As a corollary, the methods `PackageManager#getInstalledPackages` and
        `PackageManager#getInstalledApplications` will no longer return information about all
        installed apps. To query specific apps or types of apps, you can use methods like
        `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

        For more details, see https://g.co/dev/packagevisibility.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST_FILE)
      ),
      androidSpecific = true,
    )
  }
}