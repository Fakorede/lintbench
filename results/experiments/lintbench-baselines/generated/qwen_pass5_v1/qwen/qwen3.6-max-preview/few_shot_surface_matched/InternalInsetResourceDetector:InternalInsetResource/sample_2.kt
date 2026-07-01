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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private val INTERNAL_INSET_DIMENS = setOf(
      "status_bar_height",
      "navigation_bar_height",
      "system_bar_height",
      "input_method_navigation_height",
      "navigation_bar_frame_height",
      "status_bar_height_landscape",
      "navigation_bar_height_landscape",
      "navigation_bar_width",
      "navigation_bar_frame_width"
    )

    @JvmField
    val INTERNAL_INSET_RESOURCE = Issue.create(
      id = "InternalInsetResource",
      briefDescription = "Using internal inset dimension resource",
      explanation = """
        The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "getDimension",
    "getDimensionPixelSize",
    "getDimensionPixelOffset"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources", false)) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val resourceName = context.evaluator.getResourceName(argument) ?: return
    val simpleName = resourceName.substringAfterLast('/')

    if (simpleName in INTERNAL_INSET_DIMENS) {
      val location = context.getLocation(argument)
      val message = "Using internal inset dimension resource `$resourceName` is not supported. Use `WindowInsetsCompat` instead."
      context.report(Incident(INTERNAL_INSET_RESOURCE, node, location, message))
    }
  }
}