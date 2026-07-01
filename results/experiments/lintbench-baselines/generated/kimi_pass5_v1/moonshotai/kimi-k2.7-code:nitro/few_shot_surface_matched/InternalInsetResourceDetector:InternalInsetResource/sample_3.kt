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

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private val INSET_DIMENSION_RESOURCES =
      setOf(
        "status_bar_height",
        "navigation_bar_height",
        "navigation_bar_width",
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "InternalInsetResource",
        briefDescription = "Using internal inset dimension resource",
        explanation =
          """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI.

                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """
            .trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("getDimension", "getDimensionPixelOffset", "getDimensionPixelSize")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val field = context.evaluator.resolve(argument) as? PsiField ?: return

    if (field.containingClass?.name != "dimen") return
    if (field.containingClass?.containingClass?.qualifiedName != "android.R") return

    val resourceName = field.name
    if (resourceName !in INSET_DIMENSION_RESOURCES) return

    val message =
      "Using internal inset dimension resource `$resourceName` is not supported; use WindowInsetsCompat instead."
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }
}