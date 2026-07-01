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
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  companion object {
    private val APPLICABLE_METHODS = listOf(
      "sendAccessibilityEvent",
      "sendAccessibilityEventUnchecked",
      "obtain",
      "setEventType"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityWindowStateChangedEvent",
      briefDescription = "Use of accessibility window state change events is discouraged",
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
      """,
      category = Category.A11Y,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val methodName = node.methodName ?: return
    if (methodName !in APPLICABLE_METHODS) return

    var usesWindowStateChanged = false
    for (argument in node.valueArguments) {
      if (referencesWindowStateChanged(argument)) {
        usesWindowStateChanged = true
        break
      }
    }

    if (usesWindowStateChanged) {
      reportIssue(context, node)
    }
  }

  override fun getApplicableReferenceNames(): List<String> = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
    if (isWindowStateChangedField(referenced)) {
      if (!isInsideHandledMethodCall(reference)) {
        reportIssue(context, reference)
      }
    }
  }

  private fun isWindowStateChangedField(element: PsiElement?): Boolean {
    val field = element as? PsiField ?: return false
    if (field.name != "TYPE_WINDOW_STATE_CHANGED") return false
    val containingClass = field.containingClass ?: return false
    val qualifiedName = containingClass.qualifiedName ?: return false
    return qualifiedName == "android.view.accessibility.AccessibilityEvent" ||
        qualifiedName == "androidx.core.view.accessibility.AccessibilityEventCompat" ||
        qualifiedName == "android.support.v4.view.accessibility.AccessibilityEventCompat"
  }

  private fun isInsideHandledMethodCall(reference: UReferenceExpression): Boolean {
    var parent = reference.uastParent
    while (parent != null) {
      if (parent is UCallExpression) {
        val methodName = parent.methodName
        if (methodName in APPLICABLE_METHODS) {
          return true
        }
      }
      parent = parent.uastParent
    }
    return false
  }

  private fun referencesWindowStateChanged(expression: UExpression): Boolean {
    if (expression is USimpleNameReferenceExpression) {
      if (isWindowStateChangedField(expression.resolve())) {
        return true
      }
    }
    for (child in expression.uastChildren) {
      if (child is UExpression && referencesWindowStateChanged(child)) {
        return true
      }
    }
    return false
  }

  private fun reportIssue(context: JavaContext, node: UElement) {
    val location = context.getLocation(node)
    val message = "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
        "Prefer using system-provided widgets, or APIs like `Activity.setTitle`, " +
        "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion` " +
        "to let the platform send these events automatically."
    context.report(Incident(ISSUE, node, location, message))
  }
}