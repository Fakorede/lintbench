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
    private val INTERNAL_INSET_NAMES = setOf(
      "status_bar_height",
      "navigation_bar_height",
      "navigation_bar_height_landscape",
      "system_bar_height",
      "status_bar_height_landscape"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "InternalInsetResource",
      briefDescription = "Using internal inset dimension resource",
      explanation = """
        The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getDimension", "getDimensionPixelOffset", "getDimensionPixelSize", "getIdentifier")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInSubClassOf(method, "android.content.res.Resources", false)) {
      return
    }

    val resourceName: String? = when (method.name) {
      "getIdentifier" -> {
        node.valueArguments.firstOrNull()?.evaluate() as? String
      }
      else -> {
        val arg = node.valueArguments.firstOrNull() ?: return
        context.getResourceName(arg)
      }
    }

    if (resourceName != null && INTERNAL_INSET_NAMES.any { resourceName == it || resourceName.endsWith("/$it") }) {
      val location = context.getLocation(node)
      val message = "Using internal inset dimension resource `$resourceName` is not supported. " +
        "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
      context.report(Incident(ISSUE, node, location, message))
    }
  }
}