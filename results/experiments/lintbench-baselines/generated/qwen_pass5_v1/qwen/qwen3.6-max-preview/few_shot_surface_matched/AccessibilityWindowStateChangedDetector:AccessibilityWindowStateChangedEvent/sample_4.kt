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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
    private const val CONSTANT_NAME = "TYPE_WINDOW_STATE_CHANGED"

    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events",
      explanation = """
        Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
        Instead, prefer to use or extend system-provided widgets that are as far down Android's
        class hierarchy as possible. System-provided widgets that are far down the hierarchy already
        have most of the accessibility capabilities your app needs. If you must extend `View` or
        `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`,
        `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
        implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls)
        implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These
        approaches allow accessibility services to inspect the view hierarchy, rather than relying
        on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will
        be sent automatically when updating this metadata, and so trying to manually send this event
        will result in duplicate events, or the event may be ignored entirely.
      """.trimIndent(),
      category = Category.ACCESSIBILITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        AccessibilityWindowStateChangedDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      )
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("sendAccessibilityEvent", "setType")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val isInView = evaluator.isMemberInSubClassOf(method, "android.view.View")
    val isInAccessibilityEvent = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_EVENT_CLASS)

    if (!isInView && !isInAccessibilityEvent) return

    for (arg in node.valueArguments) {
      if (isWindowStateConstant(context, arg)) {
        val location = context.getLocation(arg)
        val message = "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
          "Prefer using system widgets or setting accessibility metadata directly."
        context.report(Incident(ISSUE, arg, location, message))
        return
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String> = listOf(CONSTANT_NAME)

  override fun visitReference(context: JavaContext, node: UReferenceExpression, referenced: PsiElement) {
    if (referenced is PsiField) {
      val containingClass = referenced.containingClass
      if (containingClass != null &&
        context.evaluator.extendsClass(containingClass, ACCESSIBILITY_EVENT_CLASS, false) &&
        referenced.name == CONSTANT_NAME) {
        val location = context.getLocation(node)
        val message = "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
          "Prefer using system widgets or setting accessibility metadata directly."
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }

  private fun isWindowStateConstant(context: JavaContext, expression: UExpression): Boolean {
    val ref = expression as? UReferenceExpression ?: return false
    val resolved = ref.resolve() as? PsiField ?: return false
    val cls = resolved.containingClass ?: return false
    return resolved.name == CONSTANT_NAME &&
      context.evaluator.extendsClass(cls, ACCESSIBILITY_EVENT_CLASS, false)
  }
}