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
import org.jetbrains.uast.getParentOfType

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val CHROME_OS_ON_CONFIGURATION_CHANGED =
      Issue.create(
        id = "ChromeOsOnConfigurationChanged",
        briefDescription = "Avoid UI redraw logic inside onConfigurationChanged()",
        explanation =
          """
                When users resize the Android emulator in Android 13 and on Chrome OS, an
                `onConfigurationChanged()` call occurs. If that method contains code that can cause
                a UI redraw, your app may take a performance hit on large screens. Ensure that
                `onConfigurationChanged()` does not call view invalidation or request-layout APIs.
            """,
        category = Category.CHROME_OS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
    private const val VIEW_CLASS = "android.view.View"

    private val REDRAW_METHODS =
      setOf(
        "invalidate",
        "postInvalidate",
        "requestLayout",
        "forceLayout",
        "layout",
        "measure",
        "refreshDrawableState",
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val psi = node.javaPsi as? PsiMethod ?: return
        this@ChromeOsSourceDetector.visitMethod(context, node, psi)
      }

      override fun visitCallExpression(node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName !in REDRAW_METHODS) return
        val method = node.resolve() ?: return
        this@ChromeOsSourceDetector.visitMethodCall(context, node, method)
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS.toList()

  override fun visitMethod(context: JavaContext, node: UMethod, method: PsiMethod) {
    // SourceCodeScanner method-declaration callback. Current-method tracking is performed
    // directly from each call site, so no state is kept here.
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isInsideOnConfigurationChanged(node)) return
    if (!context.evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) return

    val message =
      "Avoid calling ${node.methodName}(...) inside onConfigurationChanged(); it can trigger a UI redraw and cause poor performance on Chrome OS and large screens."
    context.report(
      Incident(
        CHROME_OS_ON_CONFIGURATION_CHANGED,
        node,
        context.getLocation(node),
        message,
      ),
    )
  }

  private fun isInsideOnConfigurationChanged(node: UCallExpression): Boolean {
    val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return false
    return isOnConfigurationChanged(containingMethod)
  }

  private fun isOnConfigurationChanged(node: UMethod): Boolean {
    if (node.name != "onConfigurationChanged") return false
    val parameters = node.parameterList.parameters
    return parameters.size == 1 && parameters[0].type.canonicalText == CONFIGURATION_CLASS
  }
}