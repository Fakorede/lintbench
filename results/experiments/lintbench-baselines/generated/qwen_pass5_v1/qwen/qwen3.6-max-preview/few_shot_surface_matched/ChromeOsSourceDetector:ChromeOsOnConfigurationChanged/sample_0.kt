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
    val ISSUE = Issue.create(
      id = "ChromeOsOnConfigurationChanged",
      briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
      explanation = """
        When users resize the Android emulator in Android 13 and Chrome OS, an \
        `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
        method contains any code that can cause a redraw, your app might take a performance \
        hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
        does not contain any calls to UI redraw logic for specific elements.
      """.trimIndent(),
      category = Category.create("CHROME_OS", "Chrome OS"),
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )

    private val REDRAW_METHODS = listOf(
      "requestLayout", "invalidate", "postInvalidate", "forceLayout", "setLayoutParams"
    )
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return null
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? {
    return listOf(UMethod::class.java, UCallExpression::class.java)
  }

  override fun visitMethod(context: JavaContext, node: UMethod) {
    // Method entry tracking; primary logic delegated to visitMethodCall for precision
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    // General call expression visitor; method-specific checks are in visitMethodCall
  }

  override fun getApplicableMethodNames(): List<String>? {
    return REDRAW_METHODS
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingMethod = node.getContainingUMethod()
    if (containingMethod?.name != "onConfigurationChanged") {
      return
    }

    val location = context.getLocation(node)
    val message = "Calling ${node.methodName}() inside onConfigurationChanged() may cause poor performance on Chrome OS and large screens. Avoid UI redraw logic here."
    context.report(Incident(ISSUE, node, location, message))
  }
}