package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityFocus",
        briefDescription = "Forcing accessibility focus",
        explanation =
          """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """
            .trimIndent(),
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("performAction", "sendAccessibilityEvent", "sendAccessibilityEventUnchecked")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val name = method.name
    val args = node.valueArguments
    if (args.isEmpty()) return

    val firstArg = args[0]
    val evaluated = firstArg.evaluate()

    if (name == "performAction") {
      if (evaluated == 64 || isAccessibilityFocusAction(firstArg)) {
        reportForceFocus(context, node)
      }
    } else if (name == "sendAccessibilityEvent" || name == "sendAccessibilityEventUnchecked") {
      if (evaluated == 32768 || isAccessibilityFocusEvent(firstArg)) {
        reportForceFocus(context, node)
      }
    }
  }

  override fun visitSimpleNameReferenceExpression(
    context: JavaContext,
    node: USimpleNameReferenceExpression,
  ) {
    val name = node.identifier
    if (name == "ACTION_ACCESSIBILITY_FOCUS" || name == "TYPE_VIEW_ACCESSIBILITY_FOCUSED") {
      reportForceFocus(context, node)
    }
  }

  private fun isAccessibilityFocusAction(expression: UExpression): Boolean {
    val text = expression.asSourceString()
    return text.contains("ACTION_ACCESSIBILITY_FOCUS")
  }

  private fun isAccessibilityFocusEvent(expression: UExpression): Boolean {
    val text = expression.asSourceString()
    return text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")
  }

  private fun reportForceFocus(context: JavaContext, node: UElement) {
    context.report(
      ISSUE,
      node,
      context.getLocation(node),
      "Forcing accessibility focus is discouraged as it interferes with screen readers",
    )
  }
}