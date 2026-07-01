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
import org.jetbrains.uast.getParentOfType

private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
private const val VIEW_CLASS = "android.view.View"

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityWindowStateChangedEvent",
        briefDescription = "Avoid using AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED",
        explanation =
          """
                Sending or populating `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs.

                If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.

                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event will result in duplicate events, or the event may be ignored entirely.
            """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("obtain", "sendAccessibilityEvent", "setEventType")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      "obtain" -> checkObtain(context, node, method)
      "sendAccessibilityEvent" -> checkSendAccessibilityEvent(context, node, method)
      "setEventType" -> checkSetEventType(context, node, method)
    }
  }

  override fun getApplicableReferenceNames() = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(context: JavaContext, node: UReferenceExpression) {
    val resolved = node.resolve() as? PsiField ?: return
    if (!context.evaluator.isMemberInClass(resolved, ACCESSIBILITY_EVENT_CLASS)) return

    val parentCall = node.getParentOfType(UCallExpression::class.java, false)
    if (parentCall != null && isHandledByDetector(context, parentCall)) {
      return
    }

    val location = context.getLocation(node)
    val message =
      "Avoid using AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED; accessibility services will receive this event automatically when accessibility metadata is set correctly."
    context.report(Incident(ISSUE, node, location, message))
  }

  private fun checkObtain(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, ACCESSIBILITY_EVENT_CLASS)) return
    if (node.valueArgumentCount != 1) return
    if (isTypeWindowStateChanged(context, node.valueArguments[0])) {
      report(
        context,
        node,
        "Avoid creating an AccessibilityEvent with TYPE_WINDOW_STATE_CHANGED; accessibility services will receive this event automatically when accessibility metadata is set correctly.",
      )
    }
  }

  private fun checkSendAccessibilityEvent(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) return
    if (node.valueArgumentCount != 1) return
    if (isTypeWindowStateChanged(context, node.valueArguments[0])) {
      report(
        context,
        node,
        "Avoid sending AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED manually; use View.onInitializeAccessibilityNodeInfo or accessibility metadata instead.",
      )
    }
  }

  private fun checkSetEventType(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_EVENT_CLASS)) return
    if (node.valueArgumentCount != 1) return
    if (isTypeWindowStateChanged(context, node.valueArguments[0])) {
      report(
        context,
        node,
        "Avoid populating AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED; accessibility services will receive this event automatically when accessibility metadata is set correctly.",
      )
    }
  }

  private fun isTypeWindowStateChanged(context: JavaContext, expression: UExpression?): Boolean {
    if (expression == null) return false
    val ref = expression as? UReferenceExpression ?: return false
    val resolved = ref.resolve() as? PsiField ?: return false
    return resolved.name == "TYPE_WINDOW_STATE_CHANGED" &&
      context.evaluator.isMemberInClass(resolved, ACCESSIBILITY_EVENT_CLASS)
  }

  private fun isHandledByDetector(context: JavaContext, call: UCallExpression): Boolean {
    val method = call.resolve() ?: return false
    return when (method.name) {
      "obtain" -> context.evaluator.isMemberInClass(method, ACCESSIBILITY_EVENT_CLASS)
      "sendAccessibilityEvent" -> context.evaluator.isMemberInSubClassOf(method, VIEW_CLASS)
      "setEventType" -> context.evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_EVENT_CLASS)
      else -> false
    }
  }

  private fun report(context: JavaContext, node: UCallExpression, message: String) {
    val location = context.getLocation(node)
    context.report(Incident(ISSUE, node, location, message))
  }
}