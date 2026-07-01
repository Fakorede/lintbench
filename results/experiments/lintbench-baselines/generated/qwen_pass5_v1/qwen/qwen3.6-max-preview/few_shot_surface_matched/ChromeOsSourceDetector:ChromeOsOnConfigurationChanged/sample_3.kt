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
import org.jetbrains.uast.getContainingUMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ChromeOsOnConfigurationChanged",
      briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
      explanation = "When users resize the Android emulator in Android 13 and Chrome OS, an onConfigurationChanged() API call occurs. If your onConfigurationChanged() method contains any code that can cause a redraw, your app might take a performance hit on large screens. Ensure your onConfigurationChanged() method does not contain any calls to UI redraw logic for specific elements.",
      category = Category.CHROME_OS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )

    private val REDRAW_METHODS = setOf(
      "invalidate", "requestLayout", "setContentView",
      "postInvalidate", "forceLayout", "notifyDataSetChanged"
    )
  }

  override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS.toList()

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // Hook for method declarations
      }

      override fun visitCallExpression(node: UCallExpression) {
        checkRedrawCall(context, node)
      }
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    checkRedrawCall(context, node)
  }

  private fun checkRedrawCall(context: JavaContext, node: UCallExpression) {
    val containingMethod = node.getContainingUMethod()
    if (containingMethod?.name == "onConfigurationChanged" && node.methodName in REDRAW_METHODS) {
      val location = context.getLocation(node)
      val message = "Calling ${node.methodName}() inside onConfigurationChanged() can cause poor performance on Chrome OS and large screens."
      context.report(Incident(ISSUE, node, location, message))
    }
  }
}