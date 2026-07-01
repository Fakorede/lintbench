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
import org.jetbrains.uast.tryResolve

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityFocus",
      briefDescription = "Forcing accessibility focus",
      explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
      category = Category.ACCESSIBILITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "requestAccessibilityFocus",
    "sendAccessibilityEvent",
    "performAccessibilityAction"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val methodName = method.name

    if (methodName == "requestAccessibilityFocus") {
      if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
        val message = "Do not force accessibility focus; let the user navigate naturally."
        context.report(Incident(ISSUE, node, context.getLocation(node), message))
      }
      return
    }

    if (methodName == "sendAccessibilityEvent") {
      if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isConstant(arg, "android.view.accessibility.AccessibilityEvent", "TYPE_VIEW_FOCUSED")) {
          val message = "Do not force accessibility focus; let the user navigate naturally."
          context.report(Incident(ISSUE, node, context.getLocation(node), message))
        }
      }
      return
    }

    if (methodName == "performAccessibilityAction") {
      if (evaluator.isMemberInSubClassOf(method, "android.view.View", false) ||
          evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false)) {
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isConstant(arg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS")) {
          val message = "Do not force accessibility focus; let the user navigate naturally."
          context.report(Incident(ISSUE, node, context.getLocation(node), message))
        }
      }
    }
  }

  override fun visitSimpleNameReferenceExpression(context: JavaContext, node: UReferenceExpression, psi: PsiElement) {
    val field = psi as? PsiField ?: return
    val containingClass = field.containingClass?.qualifiedName ?: return
    val fieldName = field.name

    val message = "Do not force accessibility focus; let the user navigate naturally."
    if (containingClass == "android.view.accessibility.AccessibilityEvent" && fieldName == "TYPE_VIEW_FOCUSED") {
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    } else if (containingClass == "android.view.accessibility.AccessibilityNodeInfo" && fieldName == "ACTION_ACCESSIBILITY_FOCUS") {
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  private fun isConstant(expression: UExpression, className: String, fieldName: String): Boolean {
    val resolved = expression.tryResolve() as? PsiField ?: return false
    return resolved.containingClass?.qualifiedName == className && resolved.name == fieldName
  }
}