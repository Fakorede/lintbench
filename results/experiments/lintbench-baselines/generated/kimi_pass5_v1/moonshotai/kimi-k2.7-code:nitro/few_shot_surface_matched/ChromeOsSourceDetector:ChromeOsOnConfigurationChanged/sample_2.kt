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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
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
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain calls to UI redraw logic for specific elements, such as `View#invalidate()`, `View#requestLayout()`, or `View#forceLayout()`.
            """,
        category = Category.CHROME_OS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val REDRAW_METHODS =
      listOf(
        "invalidate",
        "requestLayout",
        "forceLayout",
        "postInvalidate",
        "postInvalidateOnAnimation",
        "requestFocus",
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

  override fun createUastHandler(context: JavaContext): UElementHandler? =
    super<SourceCodeScanner>.createUastHandler(context)

  override fun visitMethod(context: JavaContext, node: UMethod) {
    super<SourceCodeScanner>.visitMethod(context, node)
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    super<SourceCodeScanner>.visitCallExpression(context, node)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isInsideOnConfigurationChanged(context, node)) {
      return
    }
    if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View")) {
      return
    }

    val location = context.getLocation(node)
    val message =
      "Avoid calling ${method.name}() inside onConfigurationChanged(); it can cause redundant redraws and poor performance on large screens."
    context.report(Incident(ISSUE, node, location, message))
  }

  private fun isInsideOnConfigurationChanged(context: JavaContext, node: UCallExpression): Boolean {
    var current = node.uastParent
    while (current != null) {
      if (current is UMethod && isOnConfigurationChanged(context, current)) {
        return true
      }
      current = current.uastParent
    }
    return false
  }

  private fun isOnConfigurationChanged(context: JavaContext, method: UMethod): Boolean {
    if (method.name != "onConfigurationChanged") {
      return false
    }
    val parameters = method.parameterList.parameters
    if (parameters.size != 1) {
      return false
    }
    if (parameters[0].type.canonicalText != "android.content.res.Configuration") {
      return false
    }
    return context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")
  }
}