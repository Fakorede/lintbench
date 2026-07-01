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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ACCESSIBILITY_NODE_INFO = "android.view.accessibility.AccessibilityNodeInfo"
    private const val ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"
    private const val VIEW = "android.view.View"

    private const val ACTION_ACCESSIBILITY_FOCUS_VALUE: Int = 64 // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
    private const val TYPE_VIEW_FOCUSED_VALUE: Int = 0x00008000 // AccessibilityEvent.TYPE_VIEW_FOCUSED

    @JvmField
    val ACCESSIBILITY_FOCUS =
      Issue.create(
        id = "AccessibilityFocus",
        briefDescription = "Forcing accessibility focus",
        explanation =
          """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """,
        category = Category.A11Y,
        priority = 3,
        severity = Severity.WARNING,
        implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("performAction", "requestAccessibilityFocus", "sendAccessibilityEvent")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      "requestAccessibilityFocus" -> {
        if (context.evaluator.isMemberInSubClassOf(method, VIEW)) {
          report(context, node, "Calling requestAccessibilityFocus() forces accessibility focus and can interfere with screen readers.")
        }
      }
      "performAction" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO)) return
        val argument = node.valueArguments.firstOrNull() ?: return
        if (isAccessibilityFocusArgument(argument)) {
          report(context, node, "Passing ACTION_ACCESSIBILITY_FOCUS to performAction forces accessibility focus.")
        }
      }
      "sendAccessibilityEvent" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, VIEW)) return
        val argument = node.valueArguments.firstOrNull() ?: return
        if (isTypeViewFocusedArgument(argument)) {
          report(context, node, "Sending TYPE_VIEW_FOCUSED forces accessibility focus.")
        }
      }
    }
  }

  override fun visitSimpleNameReferenceExpression(
    context: JavaContext,
    node: USimpleNameReferenceExpression,
  ) {
    when (node.identifier) {
      "ACTION_ACCESSIBILITY_FOCUS" -> {
        val field = node.resolve() as? PsiField ?: return
        if (field.containingClass?.qualifiedName != ACCESSIBILITY_NODE_INFO) return
        if (isInsideMethodCall(node, "performAction")) return
        report(context, node, "Referencing ACTION_ACCESSIBILITY_FOCUS can force accessibility focus.")
      }
      "TYPE_VIEW_FOCUSED" -> {
        val field = node.resolve() as? PsiField ?: return
        if (field.containingClass?.qualifiedName != ACCESSIBILITY_EVENT) return
        if (isInsideMethodCall(node, "sendAccessibilityEvent")) return
        report(context, node, "Referencing TYPE_VIEW_FOCUSED can force accessibility focus.")
      }
    }
  }

  private fun isAccessibilityFocusArgument(argument: UExpression): Boolean {
    val resolved = (argument as? UReferenceExpression)?.resolve()
    if (resolved is PsiField) {
      return resolved.name == "ACTION_ACCESSIBILITY_FOCUS" &&
        resolved.containingClass?.qualifiedName == ACCESSIBILITY_NODE_INFO
    }
    val value = argument.evaluate()
    return value is Number && value.toInt() == ACTION_ACCESSIBILITY_FOCUS_VALUE
  }

  private fun isTypeViewFocusedArgument(argument: UExpression): Boolean {
    val resolved = (argument as? UReferenceExpression)?.resolve()
    if (resolved is PsiField) {
      return resolved.name == "TYPE_VIEW_FOCUSED" &&
        resolved.containingClass?.qualifiedName == ACCESSIBILITY_EVENT
    }
    val value = argument.evaluate()
    return value is Number && value.toInt() == TYPE_VIEW_FOCUSED_VALUE
  }

  private fun isInsideMethodCall(node: USimpleNameReferenceExpression, methodName: String): Boolean {
    var current: UElement? = node
    while (current != null && current !is UMethod) {
      val parent = current.uastParent
      if (parent is UCallExpression && parent.methodName == methodName) {
        return true
      }
      current = parent
    }
    return false
  }

  private fun report(context: JavaContext, node: UElement, message: String) {
    context.report(Incident(ACCESSIBILITY_FOCUS, node, context.getLocation(node), message))
  }
}