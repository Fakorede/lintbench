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
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ChromeOsOnConfigurationChanged",
        briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
        explanation =
          """
            When users resize the Android emulator in Android 13 and Chrome OS, an \
            `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
            method contains any code that can cause a redraw, your app might take a performance \
            hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
            does not contain any calls to UI redraw logic for specific elements.
          """.trimIndent(),
        category = Category.CHROME_OS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val REDRAW_METHODS = listOf("invalidate", "requestLayout", "postInvalidate", "setLayoutParams")
  }

  override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (isInsideOnConfigurationChanged(node)) {
      val message =
        "Calling `${node.methodName}` inside `onConfigurationChanged` can cause poor performance on Chrome OS and large screens."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UCallExpression::class.java, UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? = null

  override fun visitMethod(context: JavaContext, node: UMethod) {
    // Provided for API compliance; traversal logic handled via method call visitor.
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    // Provided for API compliance; targeted method filtering handled via visitMethodCall.
  }

  private fun isInsideOnConfigurationChanged(node: UCallExpression): Boolean {
    var parent = node.uastParent
    while (parent != null) {
      if (parent is UMethod && parent.name == "onConfigurationChanged") {
        return true
      }
      parent = parent.uastParent
    }
    return false
  }
}