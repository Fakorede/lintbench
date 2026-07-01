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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
    private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
    private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
    private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityFocus",
        briefDescription = "Forcing accessibility focus",
        explanation =
          "Forcing accessibility focus interferes with screen readers and gives an " +
            "inconsistent user experience, especially across apps.",
        category = Category.ACCESSIBILITY,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("sendAccessibilityEvent", "performAccessibilityAction", "performAction")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val methodName = node.methodName ?: return
    val arg = node.valueArguments.firstOrNull() ?: return
    val resolvedField = resolveToField(arg) ?: return

    val containingClass = resolvedField.containingClass?.qualifiedName ?: return

    val isFocusEvent =
      methodName == "sendAccessibilityEvent" &&
        containingClass == ACCESSIBILITY_EVENT_CLASS &&
        resolvedField.name == TYPE_VIEW_ACCESSIBILITY_FOCUSED

    val isFocusAction =
      (methodName == "performAccessibilityAction" || methodName == "performAction") &&
        containingClass == ACCESSIBILITY_NODE_INFO_CLASS &&
        resolvedField.name == ACTION_ACCESSIBILITY_FOCUS

    if (isFocusEvent || isFocusAction) {
      val message =
        "Forcing accessibility focus interferes with screen readers and gives an " +
          "inconsistent user experience, especially across apps."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  override fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
    val resolved = node.resolve() as? PsiField ?: return
    val containingClass = resolved.containingClass?.qualifiedName ?: return
    val fieldName = resolved.name

    val isFocusConstant =
      (containingClass == ACCESSIBILITY_EVENT_CLASS && fieldName == TYPE_VIEW_ACCESSIBILITY_FOCUSED) ||
        (containingClass == ACCESSIBILITY_NODE_INFO_CLASS && fieldName == ACTION_ACCESSIBILITY_FOCUS)

    if (isFocusConstant) {
      val message =
        "Forcing accessibility focus interferes with screen readers and gives an " +
          "inconsistent user experience, especially across apps."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  private fun resolveToField(arg: UExpression): PsiField? {
    return (arg as? UReferenceExpression)?.resolve() as? PsiField
  }
}