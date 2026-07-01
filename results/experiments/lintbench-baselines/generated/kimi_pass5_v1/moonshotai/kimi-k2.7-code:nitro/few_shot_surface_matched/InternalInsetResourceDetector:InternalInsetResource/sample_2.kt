package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceReference
import com.android.tools.lint.detector.api.ResourceType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private val INSET_RESOURCE_NAMES =
      setOf(
        "status_bar_height",
        "status_bar_height_portrait",
        "status_bar_height_landscape",
        "navigation_bar_height",
        "navigation_bar_height_portrait",
        "navigation_bar_height_landscape",
        "navigation_bar_width",
        "navigation_bar_width_portrait",
        "navigation_bar_width_landscape",
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "InternalInsetResource",
        briefDescription = "Using internal inset dimension resource",
        explanation =
          """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() =
    listOf("getDimension", "getDimensionPixelOffset", "getDimensionPixelSize")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val resource = ResourceReference.get(argument) ?: return
    if (resource.type != ResourceType.DIMEN) return
    if (resource.name !in INSET_RESOURCE_NAMES) return

    val message =
      "Using internal inset dimension resource (${resource.name}) is not recommended. Use WindowInsetsCompat instead."
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }
}