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
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events",
      explanation = """
        Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. \
        Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. \
        System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs. \
        If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, \
        `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; \
        and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
        These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. \
        Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event \
        will result in duplicate events, or the event may be ignored entirely.
      """.trimIndent(),
      category = Category.ACCESSIBILITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )

    private const val TARGET_CLASS = "android.view.accessibility.AccessibilityEvent"
    private const val TARGET_FIELD_NAME = "TYPE_WINDOW_STATE_CHANGED"
  }

  override fun getApplicableMethodNames(): List<String>? = listOf("sendAccessibilityEvent", "setEventType", "obtain")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInSubClassOf(method, TARGET_CLASS, true) &&
      !evaluator.isMemberInSubClassOf(method, "android.view.View", true)
    ) {
      return
    }

    for (arg in node.valueArguments) {
      if (arg is UReferenceExpression) {
        val resolved = arg.resolve()
        if (resolved is PsiField && isTargetField(context, resolved)) {
          reportIssue(context, arg)
          return
        }
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String>? = listOf(TARGET_FIELD_NAME)

  override fun visitReference(context: JavaContext, reference: UReferenceExpression) {
    val resolved = reference.resolve()
    if (resolved is PsiField && isTargetField(context, resolved)) {
      reportIssue(context, reference)
    }
  }

  private fun isTargetField(context: JavaContext, field: PsiField): Boolean {
    return field.name == TARGET_FIELD_NAME &&
      context.evaluator.getQualifiedName(field.containingClass) == TARGET_CLASS
  }

  private fun reportIssue(context: JavaContext, node: UElement) {
    val location = context.getLocation(node)
    val message = "Avoid manually sending or populating TYPE_WINDOW_STATE_CHANGED events. " +
      "Prefer using system widgets or setting accessibility metadata instead."
    context.report(Incident(ISSUE, node, location, message))
  }
}