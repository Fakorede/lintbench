package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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

private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
private const val QUERY_ALL_PACKAGES_SHORT = "QUERY_ALL_PACKAGES"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val QUERY_ALL_PACKAGES_SINCE_API = 30
private const val MESSAGE = "Using the QUERY_ALL_PACKAGES permission is rarely necessary; prefer a `<queries>` declaration in the manifest. See https://g.co/dev/packagevisibility."

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> = listOf("uses-permission")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(ANDROID_URI, "name") ?: return
    val name = attr.value
    if (name == QUERY_ALL_PACKAGES || name == QUERY_ALL_PACKAGES_SHORT) {
      context.report(ISSUE, attr, context.getValueLocation(attr), MESSAGE)
    }
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "checkSelfPermission",
    "checkCallingPermission",
    "checkCallingOrSelfPermission",
    "checkPermission",
    "enforcePermission"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arg = node.valueArguments.firstOrNull() ?: return
    val permission = ConstantEvaluator.evaluateString(context, arg, false) ?: return
    if (permission == QUERY_ALL_PACKAGES || permission == QUERY_ALL_PACKAGES_SHORT) {
      context.report(ISSUE, node, context.getLocation(node), MESSAGE)
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean? {
    return if (context.mainProject.buildTargetSdkVersion.featureLevel < QUERY_ALL_PACKAGES_SINCE_API) {
      false
    } else {
      null
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "QueryAllPackagesPermission",
      briefDescription = "Using QUERY_ALL_PACKAGES permission",
      explanation =
        """
                If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission.
                
                See https://g.co/dev/packagevisibility for more details.
            """,
      category = Category.COMPLIANCE,
      priority = 9,
      severity = Severity.ERROR,
      implementation = Implementation(
        PackageVisibilityDetector::class.java,
        java.util.EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
      ),
      androidSpecific = true,
    )
  }
}