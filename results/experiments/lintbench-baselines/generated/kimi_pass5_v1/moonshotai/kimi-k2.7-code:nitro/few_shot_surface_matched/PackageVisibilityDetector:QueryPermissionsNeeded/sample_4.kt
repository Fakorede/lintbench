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
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  private var hasQueryAllPackages = false

  override fun getApplicableElements(): Collection<String> =
    listOf("uses-permission", "queries")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName == "uses-permission") {
      val name = element.getAttributeNS(ANDROID_URI, "name")
      if (name == "android.permission.QUERY_ALL_PACKAGES") {
        hasQueryAllPackages = true
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("getInstalledPackages", "getInstalledApplications")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val message =
      "Using PackageManager#${method.name} on Android 11+ will not return information about " +
        "all installed apps unless QUERY_ALL_PACKAGES is declared. Consider using " +
        "PackageManager#getPackageInfo or PackageManager#queryIntentActivities instead."

    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    if (incident.issue != ISSUE) {
      return true
    }
    if (context.mainProject.targetSdk < ANDROID_11) {
      return false
    }
    if (hasQueryAllPackages) {
      return false
    }
    return true
  }

  override fun beforeCheckProject(context: Context) {
    super.beforeCheckProject(context)
    hasQueryAllPackages = false
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val ANDROID_11 = 30

    @JvmField
    val ISSUE = Issue.create(
      id = "QueryPermissionsNeeded",
      briefDescription = "Using APIs affected by query permissions",
      explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps
                by default. If you need to query or interact with other installed apps, you may
                need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` will no longer return information about
                all installed apps. To query specific apps or types of apps, you can use methods
                like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                For more details, see https://g.co/dev/packagevisibility.
            """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE),
      ),
      androidSpecific = true,
    )
  }
}