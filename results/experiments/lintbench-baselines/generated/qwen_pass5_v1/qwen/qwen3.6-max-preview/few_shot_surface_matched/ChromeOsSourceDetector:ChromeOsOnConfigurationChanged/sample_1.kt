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
    private val REDRAW_METHODS = setOf("requestLayout", "invalidate", "postInvalidate", "forceLayout")

    @JvmField
    val CHROME_OS_ON_CONFIGURATION_CHANGED =
      Issue.create(
        id = "ChromeOsOnConfigurationChanged",
        briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
        explanation =
          """
            When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
          """.trimIndent(),
        category = Category.CHROME_OS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS.toList()

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingMethod = node.getContainingUMethod() ?: return
    if (containingMethod.name == "onConfigurationChanged") {
      val message =
        "Calling ${method.name} inside onConfigurationChanged() may cause poor performance on Chrome OS and large screens."
      context.report(
        Incident(CHROME_OS_ON_CONFIGURATION_CHANGED, node, context.getLocation(node), message)
      )
    }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(): UElementHandler? {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // UAST method visitor hook for future extensibility
      }

      override fun visitCallExpression(node: UCallExpression) {
        // UAST call expression visitor hook for future extensibility
      }
    }
  }
}