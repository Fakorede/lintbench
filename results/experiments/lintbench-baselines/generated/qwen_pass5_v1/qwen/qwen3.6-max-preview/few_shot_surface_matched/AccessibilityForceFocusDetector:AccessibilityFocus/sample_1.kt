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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityFocus",
      briefDescription = "Forcing accessibility focus",
      explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
      category = Category.ACCESSIBILITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )

    private const val TYPE_VIEW_FOCUSED = "TYPE_VIEW_FOCUSED"
    private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("sendAccessibilityEvent", "performAccessibilityAction")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInSubClassOf(method, "android.view.View", true)) return

    val arg = node.valueArguments.firstOrNull() ?: return
    val resolved = evaluator.resolve(arg) as? PsiField ?: return

    val isFocusEvent = method.name == "sendAccessibilityEvent" && resolved.name == TYPE_VIEW_FOCUSED
    val isFocusAction = method.name == "performAccessibilityAction" && resolved.name == ACTION_ACCESSIBILITY_FOCUS

    if (isFocusEvent || isFocusAction) {
      report(context, node)
    }
  }

  override fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression, resolved: PsiElement?) {
    if (resolved is PsiField && (resolved.name == TYPE_VIEW_FOCUSED || resolved.name == ACTION_ACCESSIBILITY_FOCUS)) {
      report(context, node)
    }
  }

  private fun report(context: JavaContext, element: UElement) {
    context.report(
      Incident(
        ISSUE,
        element,
        context.getLocation(element),
        "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
      )
    )
  }
}