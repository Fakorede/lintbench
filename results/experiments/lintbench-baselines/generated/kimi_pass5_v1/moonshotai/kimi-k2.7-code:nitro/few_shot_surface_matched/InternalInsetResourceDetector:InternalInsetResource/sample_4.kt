package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private val DIMENSION_LOOKUP_METHODS = listOf(
      "getDimension",
      "getDimensionPixelOffset",
      "getDimensionPixelSize"
    )

    private val INSET_DIMEN_RESOURCES = setOf(
      "status_bar_height",
      "status_bar_height_default",
      "status_bar_height_portrait",
      "status_bar_height_landscape",
      "status_bar_height_large",
      "navigation_bar_height",
      "navigation_bar_width",
      "navigation_bar_height_landscape",
      "navigation_bar_width_landscape",
      "navigation_bar_height_portrait",
      "navigation_bar_width_portrait",
      "navigation_bar_height_car_mode",
      "navigation_bar_width_car_mode"
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

  override fun getApplicableMethodNames() = DIMENSION_LOOKUP_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val field = (argument as? UReferenceExpression)?.resolve() as? PsiField ?: return
    if (field.containingClass?.qualifiedName != "android.R.dimen") return
    if (field.name !in INSET_DIMEN_RESOURCES) return

    val message =
      "Using internal inset dimension resource `${field.name}`. Use WindowInsetsCompat instead."
    val location = context.getLocation(argument)
    context.report(Incident(ISSUE, node, location, message))
  }
}