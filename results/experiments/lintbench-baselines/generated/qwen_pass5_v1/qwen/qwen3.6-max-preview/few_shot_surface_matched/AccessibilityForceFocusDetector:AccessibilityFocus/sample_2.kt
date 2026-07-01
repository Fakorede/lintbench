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
    val args = node.valueArguments
    if (args.isEmpty()) return

    val firstArg = args.first()
    if (firstArg is USimpleNameReferenceExpression) {
      val resolved = firstArg.resolve()
      if (resolved is PsiField && isFocusConstant(resolved)) {
        val location = context.getLocation(node)
        val message = "Forcing accessibility focus is discouraged. It interferes with screen readers and gives an inconsistent user experience."
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }

  override fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
    val resolved = node.resolve()
    if (resolved is PsiField && isFocusConstant(resolved)) {
      val location = context.getLocation(node)
      val message = "Forcing accessibility focus is discouraged. It interferes with screen readers and gives an inconsistent user experience."
      context.report(Incident(ISSUE, node, location, message))
    }
  }

  private fun isFocusConstant(field: PsiField): Boolean {
    val name = field.name
    val className = field.containingClass?.qualifiedName ?: return false
    return (className == "android.view.accessibility.AccessibilityEvent" && name == TYPE_VIEW_FOCUSED) ||
      (className == "android.view.accessibility.AccessibilityNodeInfo" && name == ACTION_ACCESSIBILITY_FOCUS)
  }
}