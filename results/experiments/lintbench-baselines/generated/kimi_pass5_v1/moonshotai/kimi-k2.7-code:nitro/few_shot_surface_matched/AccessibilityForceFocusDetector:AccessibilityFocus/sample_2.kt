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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityFocus",
        briefDescription = "Forcing accessibility focus",
        explanation =
          """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("performAction", "performAccessibilityAction")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo") &&
        !evaluator.isMemberInSubClassOf(method, "android.view.View")
    ) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    if (referencesAccessibilityFocus(context, argument)) {
      report(context, node)
    }
  }

  override fun visitSimpleNameReferenceExpression(
    context: JavaContext,
    node: USimpleNameReferenceExpression
  ) {
    if (!referencesAccessibilityFocus(context, node)) return

    val parentCall = node.getParentOfType(UCallExpression::class.java, true)
    if (parentCall != null) {
      val firstArg = parentCall.valueArguments.firstOrNull()
      if (firstArg != null && firstArg.contains(node)) {
        return
      }
    }

    report(context, node)
  }

  private fun referencesAccessibilityFocus(
    context: JavaContext,
    expression: UExpression
  ): Boolean {
    val ref =
      when (expression) {
        is USimpleNameReferenceExpression -> expression
        is UQualifiedReferenceExpression ->
          expression.selector as? USimpleNameReferenceExpression ?: return false
        else -> return false
      }

    val resolved = ref.resolve()
    return resolved is PsiField &&
      resolved.name == "ACTION_ACCESSIBILITY_FOCUS" &&
      context.evaluator.isMemberInClass(resolved, "android.view.accessibility.AccessibilityNodeInfo")
  }

  private fun UExpression.contains(element: UElement): Boolean {
    val outerPsi = this.sourcePsi ?: return false
    val innerPsi = element.sourcePsi ?: return false
    return outerPsi.textRange.contains(innerPsi.textRange)
  }

  private fun report(context: JavaContext, node: UElement) {
    val message = "Do not force accessibility focus; this interferes with screen readers."
    val location = context.getLocation(node)
    context.report(Incident(ISSUE, node, location, message))
  }
}