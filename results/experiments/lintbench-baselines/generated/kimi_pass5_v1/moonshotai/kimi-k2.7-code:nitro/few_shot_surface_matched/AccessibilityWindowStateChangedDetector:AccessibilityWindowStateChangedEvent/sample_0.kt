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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val TYPE_WINDOW_STATE_CHANGED: Int = 0x00000020

    private const val MESSAGE: String =
      "Use of AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED is strongly discouraged. Prefer system-provided widgets, accessibility metadata (setTitle, setAccessibilityPaneTitle, setAccessibilityLiveRegion), onInitializeAccessibilityNodeInfo, or getAccessibilityNodeProvider."

    private val SEND_METHOD_CLASSES: Set<String> = setOf(
      "android.view.View",
      "android.view.ViewParent",
      "android.view.ViewRootImpl"
    )

    @JvmField
    val ACCESSIBILITY_WINDOW_STATE_CHANGED_EVENT: Issue = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of TYPE_WINDOW_STATE_CHANGED accessibility event",
      explanation = """
                Sending or populating `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` is strongly discouraged.

                Prefer to use or extend system-provided widgets that are far down Android's class hierarchy, since they already provide most accessibility capabilities. If you must extend `View` or `Canvas` directly, set accessibility metadata using `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; and for specialized custom controls implement `View.getAccessibilityNodeProvider`.

                `TYPE_WINDOW_STATE_CHANGED` events will be sent automatically when this metadata is updated, so manually sending the event can produce duplicate or ignored events.
            """,
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

  override fun getApplicableMethodNames(): List<String> {
    return listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked", "obtain", "setEventType")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator

    when (method.name) {
      "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass !in SEND_METHOD_CLASSES) return

        for (arg in node.valueArguments) {
          if (arg.getExpressionType()?.canonicalText == "int" && isTypeWindowStateChangedLiteral(arg)) {
            reportIncident(context, node, arg)
          }
        }
      }

      "obtain" -> {
        if (!evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")) return
        val arg = node.valueArguments.firstOrNull() ?: return
        if (arg.getExpressionType()?.canonicalText == "int" && isTypeWindowStateChangedLiteral(arg)) {
          reportIncident(context, node, arg)
        }
      }

      "setEventType" -> {
        if (!evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")) return
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isTypeWindowStateChangedLiteral(arg)) {
          reportIncident(context, node, arg)
        }
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String> {
    return listOf("TYPE_WINDOW_STATE_CHANGED")
  }

  override fun visitReference(context: JavaContext, node: UReferenceExpression, field: PsiField) {
    if (!context.evaluator.isMemberInClass(field, "android.view.accessibility.AccessibilityEvent")) return

    val call = node.getParentOfType(UCallExpression::class.java, false)
    if (call != null) {
      val method = call.resolve() ?: return
      if (isRelevantMethod(context, method)) {
        context.report(
          Incident(
            ACCESSIBILITY_WINDOW_STATE_CHANGED_EVENT,
            call,
            context.getLocation(node),
            MESSAGE
          )
        )
      }
      return
    }

    val assignment = node.getParentOfType(UBinaryExpression::class.java, false)
    if (assignment != null && assignment.operator == UastBinaryOperator.ASSIGN) {
      val left = assignment.leftOperand
      if (left is UReferenceExpression &&
        (left.name == "eventType" || left.resolvedName == "eventType")
      ) {
        context.report(
          Incident(
            ACCESSIBILITY_WINDOW_STATE_CHANGED_EVENT,
            assignment,
            context.getLocation(node),
            MESSAGE
          )
        )
      }
    }
  }

  private fun isRelevantMethod(context: JavaContext, method: PsiMethod): Boolean {
    return when (method.name) {
      "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
        method.containingClass?.qualifiedName in SEND_METHOD_CLASSES
      }

      "obtain" -> {
        context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")
      }

      "setEventType" -> {
        context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")
      }

      else -> false
    }
  }

  private fun isTypeWindowStateChangedLiteral(arg: UReferenceExpression): Boolean {
    val literal = arg as? ULiteralExpression ?: return false
    return (literal.value as? Int) == TYPE_WINDOW_STATE_CHANGED
  }

  private fun reportIncident(context: JavaContext, node: UCallExpression, highlight: UReferenceExpression) {
    context.report(
      Incident(
        ACCESSIBILITY_WINDOW_STATE_CHANGED_EVENT,
        node,
        context.getLocation(highlight),
        MESSAGE
      )
    )
  }
}