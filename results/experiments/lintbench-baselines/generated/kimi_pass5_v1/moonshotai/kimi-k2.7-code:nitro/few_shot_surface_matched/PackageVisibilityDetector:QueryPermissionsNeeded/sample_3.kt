package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  private val projectsWithQueries = mutableSetOf<Project>()

  override fun getApplicableElements(): Collection<String> = listOf(TAG_QUERIES)

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    projectsWithQueries.add(context.project)
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("getInstalledPackages", "getInstalledApplications")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      return
    }

    if (hasQueriesDeclaration(context)) {
      return
    }

    val message = buildString {
      append("Using `PackageManager#")
      append(method.name)
      append("()` may not return all installed apps on Android 11 (API ")
      append(ANDROID_11)
      append(") and higher unless a `<queries>` declaration is added to the manifest")
    }

    context.report(
      Incident(
        ISSUE,
        node,
        context.getLocation(node),
        message
      )
    )
  }

  private fun hasQueriesDeclaration(context: JavaContext): Boolean {
    if (context.project in projectsWithQueries) return true
    val manifest = context.evaluator.manifest ?: return false
    return manifest.document?.getElementsByTagName(TAG_QUERIES)?.length ?: 0 > 0
  }

  override fun filterIncident(
    context: Context,
    incident: Incident,
    targetSdk: ApiConstraint,
    applicableVersions: ApiConstraint?
  ): Boolean {
    return targetSdk.isAtLeast(ANDROID_11)
  }

  companion object {
    private const val ANDROID_11 = 30
    private const val TAG_QUERIES = "queries"

    @JvmField
    val ISSUE = Issue.create(
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
      """,
      moreInfo = "https://g.co/dev/packagevisibility",
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