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
    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events",
      explanation = """
        Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. \
        Instead, prefer to use or extend system-provided widgets that are as far down Android's \
        class hierarchy as possible. System-provided widgets that are far down the hierarchy already \
        have most of the accessibility capabilities your app needs. If you must extend `View` or `Canvas` \
        directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, \
        `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; \
        implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) \
        implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
        These approaches allow accessibility services to inspect the view hierarchy, rather than \
        relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` \
        will be sent automatically when updating this metadata, and so trying to manually send this \
        event will result in duplicate events, or the event may be ignored entirely.
      """.trimIndent(),
      category = Category.A11Y,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked", "obtain")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val methodName = method.name

    if (methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") {
      if (evaluator.isMemberInSubClassOf(method, "android.view.View")) {
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isWindowStateChangedConstant(arg)) {
          context.report(
            Incident(
              ISSUE,
              node,
              context.getLocation(node),
              "Prefer using system-provided widgets or setting UI metadata instead of manually sending TYPE_WINDOW_STATE_CHANGED events."
            )
          )
        }
      }
    } else if (methodName == "obtain") {
      if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")) {
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isWindowStateChangedConstant(arg)) {
          context.report(
            Incident(
              ISSUE,
              node,
              context.getLocation(node),
              "Prefer using system-provided widgets or setting UI metadata instead of manually obtaining TYPE_WINDOW_STATE_CHANGED events."
            )
          )
        }
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String> {
    return listOf("TYPE_WINDOW_STATE_CHANGED")
  }

  override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
    val psiField = referenced as? PsiField ?: return
    if (psiField.name == "TYPE_WINDOW_STATE_CHANGED") {
      val containingClass = psiField.containingClass
      if (containingClass != null && "android.view.accessibility.AccessibilityEvent" == containingClass.qualifiedName) {
        context.report(
          Incident(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Prefer using system-provided widgets or setting UI metadata instead of manually using TYPE_WINDOW_STATE_CHANGED."
          )
        )
      }
    }
  }

  private fun isWindowStateChangedConstant(expression: UExpression): Boolean {
    val constant = expression.evaluate()
    if (constant is Int && constant == 32) {
      return true
    }
    if (expression is UReferenceExpression) {
      val resolved = expression.resolve() as? PsiField
      if (resolved != null && resolved.name == "TYPE_WINDOW_STATE_CHANGED") {
        val containingClass = resolved.containingClass
        if (containingClass != null && "android.view.accessibility.AccessibilityEvent" == containingClass.qualifiedName) {
          return true
        }
      }
    }
    return false
  }
}