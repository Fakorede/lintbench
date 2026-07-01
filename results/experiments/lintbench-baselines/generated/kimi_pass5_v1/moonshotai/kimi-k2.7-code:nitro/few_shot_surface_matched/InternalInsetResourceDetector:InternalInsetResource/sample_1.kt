package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
    @JvmField
    val INTERNAL_INSET_RESOURCE =
      Issue.create(
        id = "InternalInsetResource",
        briefDescription = "Using internal inset dimension resource",
        explanation =
          """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val INSET_RESOURCE_NAMES =
      setOf(
        "status_bar_height",
        "status_bar_height_portrait",
        "status_bar_height_landscape",
        "navigation_bar_height",
        "navigation_bar_height_landscape",
        "navigation_bar_width",
        "navigation_bar_width_landscape",
        "system_bar_height",
      )

    private val DIMENSION_ACCESS_METHODS =
      setOf("getDimension", "getDimensionPixelOffset", "getDimensionPixelSize")
  }

  override fun getApplicableMethodNames() =
    listOf("getIdentifier") + DIMENSION_ACCESS_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
      return
    }

    when (method.name) {
      "getIdentifier" -> checkGetIdentifier(context, node)
      in DIMENSION_ACCESS_METHODS -> checkDimensionAccess(context, node)
    }
  }

  private fun checkGetIdentifier(context: JavaContext, node: UCallExpression) {
    val args = node.valueArguments
    if (args.size < 3) return

    val name = ConstantEvaluator.evaluateString(context, args[0], false) ?: return
    val type = ConstantEvaluator.evaluateString(context, args[1], false) ?: return
    val defPackage = ConstantEvaluator.evaluateString(context, args[2], false)

    if (type != "dimen" && type != "dimension") return
    if (defPackage != null && defPackage != "android") return
    if (name !in INSET_RESOURCE_NAMES) return

    reportIssue(context, node, name)
  }

  private fun checkDimensionAccess(context: JavaContext, node: UCallExpression) {
    val arg = node.valueArguments.firstOrNull() ?: return
    val ref = arg as? UReferenceExpression ?: return
    val field = ref.resolve() as? PsiField ?: return
    if (field.containingClass?.name != "dimen") return
    if (field.name !in INSET_RESOURCE_NAMES) return

    reportIssue(context, node, field.name)
  }

  private fun reportIssue(context: JavaContext, node: UCallExpression, resourceName: String) {
    val message =
      "Using internal inset dimension resource \"$resourceName\" is not recommended; use WindowInsetsCompat instead"
    context.report(Incident(INTERNAL_INSET_RESOURCE, node, context.getLocation(node), message))
  }
}