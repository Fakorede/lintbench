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
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val TYPE_WINDOW_STATE_CHANGED_VALUE: Int = 32
    private const val ACCESSIBILITY_EVENT_CLASS: String = "android.view.accessibility.AccessibilityEvent"
    private const val ACCESSIBILITY_RECORD_CLASS: String = "android.view.accessibility.AccessibilityRecord"
    private const val VIEW_CLASS: String = "android.view.View"
    private const val VIEW_ACCESSIBILITY_DELEGATE_CLASS: String = "android.view.View\$AccessibilityDelegate"
    private const val VIEW_PARENT_CLASS: String = "android.view.ViewParent"
    private const val ACCESSIBILITY_MANAGER_CLASS: String = "android.view.accessibility.AccessibilityManager"

    @JvmField
    val ISSUE: Issue = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of TYPE_WINDOW_STATE_CHANGED accessibility event is discouraged",
      explanation = """
        Sending or populating `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. Prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs.

        If you must extend `View` or `Canvas` directly, prefer to set UI metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, so manually sending this event will result in duplicate events or may be ignored entirely.
      """.trimIndent(),
      category = Category.A11Y,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "obtain",
    "sendAccessibilityEvent",
    "sendAccessibilityEventUnchecked",
    "requestSendAccessibilityEvent",
    "dispatchPopulateAccessibilityEvent",
    "setEventType",
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isRelevantMethod(method)) {
      return
    }
    if (node.valueArguments.any { isWindowStateChangedEvent(it) }) {
      reportIncident(context, node)
    }
  }

  override fun getApplicableReferenceNames(): List<String> = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    if (referenced !is PsiField) {
      return
    }
    if (referenced.containingClass?.qualifiedName != ACCESSIBILITY_EVENT_CLASS) {
      return
    }
    if (reference.uastParent is UCallExpression) {
      return
    }
    reportIncident(context, reference)
  }

  private fun isRelevantMethod(method: PsiMethod): Boolean {
    val name = method.name
    val className = method.containingClass?.qualifiedName ?: return false
    return when (name) {
      "obtain" -> className == ACCESSIBILITY_EVENT_CLASS
      "setEventType" -> className == ACCESSIBILITY_RECORD_CLASS || className == ACCESSIBILITY_EVENT_CLASS
      "sendAccessibilityEvent" -> {
        className == VIEW_CLASS ||
          className == VIEW_ACCESSIBILITY_DELEGATE_CLASS ||
          className == ACCESSIBILITY_MANAGER_CLASS
      }
      "sendAccessibilityEventUnchecked" -> {
        className == VIEW_CLASS || className == VIEW_ACCESSIBILITY_DELEGATE_CLASS
      }
      "dispatchPopulateAccessibilityEvent" -> {
        className == VIEW_CLASS || className == VIEW_ACCESSIBILITY_DELEGATE_CLASS
      }
      "requestSendAccessibilityEvent" -> className == VIEW_PARENT_CLASS
      else -> false
    }
  }

  private fun isWindowStateChangedEvent(expression: UExpression?): Boolean {
    expression ?: return false
    if (expression is UReferenceExpression) {
      val resolved = expression.resolve()
      if (resolved is PsiField &&
        resolved.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS &&
        resolved.name == "TYPE_WINDOW_STATE_CHANGED"
      ) {
        return true
      }
    }
    if (expression is UCallExpression) {
      val method = expression.resolve()
      if (method?.name == "obtain" && method.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS) {
        return expression.valueArguments.any { isWindowStateChangedEvent(it) }
      }
    }
    if (expression is ULiteralExpression) {
      val value = expression.value
      return value is Int && value == TYPE_WINDOW_STATE_CHANGED_VALUE
    }
    return false
  }

  private fun reportIncident(context: JavaContext, node: UElement) {
    val location = context.getLocation(node)
    val message =
      "Sending or populating AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED events is strongly discouraged. Prefer accessibility metadata or system-provided widgets instead."
    context.report(Incident(ISSUE, node, location, message))
  }
}