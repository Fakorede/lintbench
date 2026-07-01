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
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val TYPE_WINDOW_STATE_CHANGED_FQCN =
      "android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED"

    private const val MESSAGE =
      "Avoid TYPE_WINDOW_STATE_CHANGED accessibility events; prefer system widgets or View metadata/onInitializeAccessibilityNodeInfo APIs."

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityWindowStateChangedEvent",
        briefDescription = "Use of TYPE_WINDOW_STATE_CHANGED accessibility events is discouraged",
        explanation =
          """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible.
                System-provided widgets that are far down the hierarchy already have most of the accessibility
                capabilities your app needs.
                
                If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling
                `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) implement
                `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.
                
                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on
                incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent
                automatically when updating this metadata, and so trying to manually send this event will result in
                duplicate events, or the event may be ignored entirely.
            """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE_SCOPE),
          ),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() =
    listOf("obtain", "sendAccessibilityEvent", "sendAccessibilityEventUnchecked", "requestSendAccessibilityEvent")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val name = method.name
    when (name) {
      "obtain" -> {
        if (!context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")) {
          return
        }
        val arg = node.valueArguments.firstOrNull() ?: return
        if (referencesWindowStateChanged(context, arg)) {
          reportIncident(context, node)
        }
      }
      "sendAccessibilityEvent" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
          !context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityManager")
        ) {
          return
        }
        if (node.valueArguments.any { referencesWindowStateChanged(context, it) }) {
          reportIncident(context, node)
        }
      }
      "sendAccessibilityEventUnchecked",
      "requestSendAccessibilityEvent" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
          !context.evaluator.isMemberInClass(method, "android.view.ViewParent")
        ) {
          return
        }
        if (node.valueArguments.any { referencesWindowStateChanged(context, it) }) {
          reportIncident(context, node)
        }
      }
    }
  }

  override fun getApplicableReferenceNames() = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(context: JavaContext, node: UReferenceExpression, referenced: PsiElement) {
    if (context.evaluator.resolvesTo(referenced, TYPE_WINDOW_STATE_CHANGED_FQCN)) {
      context.report(Incident(ISSUE, node, context.getLocation(node), MESSAGE))
    }
  }

  private fun referencesWindowStateChanged(context: JavaContext, expr: org.jetbrains.uast.UExpression): Boolean {
    val ref = expr as? UReferenceExpression ?: return false
    return context.evaluator.resolvesTo(ref.resolve(), TYPE_WINDOW_STATE_CHANGED_FQCN)
  }

  private fun reportIncident(context: JavaContext, node: UCallExpression) {
    context.report(Incident(ISSUE, node, context.getLocation(node), MESSAGE))
  }
}