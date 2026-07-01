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
      "status_bar_height_landscape",
      "status_bar_height_portrait",
      "navigation_bar_height",
      "navigation_bar_height_landscape",
      "navigation_bar_width",
      "system_bottom_gesture_size"
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
      implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "getDimension",
    "getDimensionPixelSize",
    "getDimensionPixelOffset"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val resourceName = context.getResourceName(argument) ?: return

    if (resourceName !in INTERNAL_INSET_DIMENS) {
      return
    }

    val location = context.getLocation(argument)
    val message = "Using internal inset dimension resource `$resourceName` is not supported. " +
      "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."

    context.report(Incident(INTERNAL_INSET_RESOURCE, node, location, message))
  }
}