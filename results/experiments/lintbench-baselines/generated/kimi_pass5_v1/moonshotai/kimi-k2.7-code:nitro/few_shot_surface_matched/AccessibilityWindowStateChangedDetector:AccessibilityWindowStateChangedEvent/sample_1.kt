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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val FIELD_NAME = "TYPE_WINDOW_STATE_CHANGED"
    private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"

    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events",
      explanation = """
        Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly
        discouraged. Instead, prefer to use or extend system-provided widgets that are as far
        down Android's class hierarchy as possible. System-provided widgets that are far down the
        hierarchy already have most of the accessibility capabilities your app needs.

        If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by
        calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or
        `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`;
        and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to
        provide a virtual view hierarchy. These approaches allow accessibility services to inspect
        the view hierarchy, rather than relying on incomplete information provided by events.

        Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this
        metadata, and so trying to manually send this event will result in duplicate events, or
        the event may be ignored entirely.
      """.trimIndent(),
      category = Category.A11Y,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        AccessibilityWindowStateChangedDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      ),
      androidSpecific = true,
    )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("sendAccessibilityEvent", "setEventType", "obtain")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      "sendAccessibilityEvent" -> checkSendAccessibilityEvent(context, node, method)
      "setEventType" -> checkSetEventType(context, node, method)
      "obtain" -> checkObtain(context, node, method)
    }
  }

  private fun checkSendAccessibilityEvent(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
  ) {
    if (node.valueArguments.size != 1) return
    val receiverClass = method.containingClass?.qualifiedName ?: return
    if (receiverClass != "android.view.View" && receiverClass != "android.view.ViewParent") return

    val arg = node.valueArguments[0]
    if (isTypeWindowStateChanged(arg)) {
      report(context, node, arg)
    }
  }

  private fun checkSetEventType(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
  ) {
    val receiverClass = method.containingClass?.qualifiedName ?: return
    if (receiverClass != "android.view.accessibility.AccessibilityEvent"
      && receiverClass != "android.view.accessibility.AccessibilityRecord") return

    val arg = node.valueArguments.firstOrNull() ?: return
    if (isTypeWindowStateChanged(arg)) {
      report(context, node, arg)
    }
  }

  private fun checkObtain(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
  ) {
    val receiverClass = method.containingClass?.qualifiedName ?: return
    if (receiverClass != ACCESSIBILITY_EVENT_CLASS) return
    if (node.valueArguments.size != 1) return

    val arg = node.valueArguments[0]
    if (isTypeWindowStateChanged(arg)) {
      report(context, node, arg)
    }
  }

  override fun getApplicableReferenceNames(): List<String> = listOf(FIELD_NAME)

  override fun visitReference(
    context: JavaContext,
    node: UReferenceExpression,
    referenced: PsiElement
  ) {
    if (!isTypeWindowStateChangedField(referenced)) return
    if (isArgumentOfTargetedCall(node)) return
    report(context, node, node)
  }

  private fun isArgumentOfTargetedCall(node: UReferenceExpression): Boolean {
    var current: UExpression? = node
    while (current != null) {
      val parent = current.uastParent
      if (parent is UCallExpression && parent.valueArguments.contains(current)) {
        val name = parent.methodName
        if (name == "sendAccessibilityEvent" || name == "setEventType" || name == "obtain") {
          return true
        }
      }
      current = parent as? UExpression
    }
    return false
  }

  private fun isTypeWindowStateChanged(expression: UExpression): Boolean {
    val ref = expression as? UReferenceExpression ?: return false
    return isTypeWindowStateChangedField(ref.resolve())
  }

  private fun isTypeWindowStateChangedField(element: PsiElement?): Boolean {
    return element is PsiField
        && element.name == FIELD_NAME
        && element.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS
  }

  private fun report(context: JavaContext, scope: UElement, highlight: UElement) {
    val location = context.getLocation(highlight)
    val message =
      "Avoid using AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED; prefer system-provided " +
        "accessibility metadata such as setAccessibilityPaneTitle or onInitializeAccessibilityNodeInfo."
    context.report(Incident(ISSUE, scope, location, message))
  }
}