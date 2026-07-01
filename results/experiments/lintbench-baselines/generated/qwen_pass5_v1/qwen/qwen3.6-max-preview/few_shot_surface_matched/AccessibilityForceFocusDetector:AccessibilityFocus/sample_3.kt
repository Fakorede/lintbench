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
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AccessibilityFocus",
      briefDescription = "Forcing accessibility focus",
      explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
      category = Category.ACCESSIBILITY,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf(
    "sendAccessibilityEvent",
    "performAccessibilityAction",
    "performAction"
  )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val isViewMethod = evaluator.isMemberInSubClassOf(method, "android.view.View", false)
    val isNodeInfoMethod = evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false)

    if (!isViewMethod && !isNodeInfoMethod) return

    val arg = node.valueArguments.firstOrNull() ?: return
    val argText = arg.asSourceString()

    val isForcingFocus = when (method.name) {
      "sendAccessibilityEvent" -> argText.contains("TYPE_VIEW_FOCUSED")
      "performAccessibilityAction", "performAction" -> argText.contains("ACTION_ACCESSIBILITY_FOCUS")
      else -> false
    }

    if (isForcingFocus) {
      val message = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  override fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
    val identifier = node.identifier
    if (identifier != "TYPE_VIEW_FOCUSED" && identifier != "ACTION_ACCESSIBILITY_FOCUS") return

    val resolved = node.resolve() as? PsiField ?: return
    val containingClass = resolved.containingClass?.qualifiedName ?: return

    val isTargetClass = containingClass == "android.view.accessibility.AccessibilityEvent" ||
      containingClass == "android.view.accessibility.AccessibilityNodeInfo"

    if (isTargetClass) {
      val message = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }
}