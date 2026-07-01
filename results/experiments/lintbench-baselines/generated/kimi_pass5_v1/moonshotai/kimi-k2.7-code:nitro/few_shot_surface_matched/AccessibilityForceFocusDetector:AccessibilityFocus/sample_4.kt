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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
    private const val ACCESSIBILITY_NODE_INFO = "android.view.accessibility.AccessibilityNodeInfo"
    private const val ACCESSIBILITY_NODE_INFO_COMPAT = "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
    private const val VIEW = "android.view.View"

    private const val MESSAGE =
      "Do not force accessibility focus; this interferes with screen readers and creates an inconsistent user experience."

    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityFocus",
      briefDescription = "Avoid forcing accessibility focus",
      explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
      category = Category.A11Y,
      priority = 5,
      severity = Severity.WARNING,
      androidSpecific = true,
      implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames() = listOf("performAction", "performAccessibilityAction")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val argument = node.valueArguments.firstOrNull() ?: return

    // Unqualified references are reported by visitSimpleNameReferenceExpression.
    if (argument is USimpleNameReferenceExpression) return
    if (!isAccessibilityFocusAction(argument)) return

    when (node.methodName) {
      "performAction" -> {
        if (context.evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO) ||
          context.evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO_COMPAT)
        ) {
          report(context, node)
        }
      }
      "performAccessibilityAction" -> {
        if (context.evaluator.isMemberInSubClassOf(method, VIEW)) {
          report(context, node)
        }
      }
    }
  }

  override fun visitSimpleNameReferenceExpression(
    context: JavaContext,
    node: USimpleNameReferenceExpression
  ) {
    if (node.uastParent is UQualifiedReferenceExpression) return
    val field = node.resolve() as? PsiField ?: return
    if (isAccessibilityFocusConstant(field)) {
      report(context, node)
    }
  }

  private fun isAccessibilityFocusAction(argument: UExpression): Boolean {
    val reference = argument as? UReferenceExpression ?: return false
    val field = reference.resolve() as? PsiField ?: return false
    return isAccessibilityFocusConstant(field)
  }

  private fun isAccessibilityFocusConstant(field: PsiField): Boolean {
    return field.name == ACTION_ACCESSIBILITY_FOCUS &&
      (field.containingClass?.qualifiedName == ACCESSIBILITY_NODE_INFO ||
        field.containingClass?.qualifiedName == ACCESSIBILITY_NODE_INFO_COMPAT)
  }

  private fun report(context: JavaContext, node: UExpression) {
    context.report(Incident(ISSUE, node, context.getLocation(node), MESSAGE))
  }
}