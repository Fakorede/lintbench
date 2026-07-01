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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
    private const val VIEW_CLASS = "android.view.View"
    private const val TYPE_WINDOW_STATE_CHANGED_VALUE = 32

    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events",
      explanation = """
        Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
        Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible.
        System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs.
        If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`,
        `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`;
        and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.
        These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events.
        Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event
        will result in duplicate events, or the event may be ignored entirely.
      """.trimIndent(),
      category = Category.ACCESSIBILITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("setType", "sendAccessibilityEvent", "obtain")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val isInTargetClass = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_EVENT_CLASS) ||
      evaluator.isMemberInSubClassOf(method, VIEW_CLASS)

    if (!isInTargetClass) return

    for (arg in node.valueArguments) {
      if (arg is ULiteralExpression && arg.value == TYPE_WINDOW_STATE_CHANGED_VALUE) {
        val message = "Manually sending or populating TYPE_WINDOW_STATE_CHANGED events is discouraged."
        val location = context.getLocation(arg)
        context.report(Incident(ISSUE, node, location, message))
        return
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String> = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(context: JavaContext, node: UReferenceExpression, resolved: PsiElement?) {
    if (resolved is PsiField) {
      val containingClass = resolved.containingClass
      if (containingClass != null &&
        containingClass.qualifiedName == ACCESSIBILITY_EVENT_CLASS &&
        resolved.name == "TYPE_WINDOW_STATE_CHANGED") {
        val message = "Manually sending or populating TYPE_WINDOW_STATE_CHANGED events is discouraged."
        val location = context.getLocation(node)
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }
}