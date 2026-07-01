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
import org.jetbrains.uast.UastUtils

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  private var reportedCalls: MutableSet<UCallExpression>? = null

  companion object {
    private val REDRAW_METHODS = listOf(
      "invalidate",
      "requestLayout",
      "forceLayout",
      "postInvalidate",
      "postInvalidateOnAnimation",
      "layout",
      "setLayoutParams",
      "setPadding",
      "setVisibility",
      "setAlpha",
      "setBackground",
      "setBackgroundColor",
      "setBackgroundResource",
      "setText",
      "setImageDrawable",
      "setImageResource",
      "setImageBitmap",
      "recreate"
    )

    @JvmField
    val CHROME_OS_ON_CONFIGURATION_CHANGED = Issue.create(
      id = "ChromeOsOnConfigurationChanged",
      briefDescription = "Avoid UI redraw logic inside onConfigurationChanged()",
      explanation = """
        When the app is resized on Android 13 or Chrome OS, `onConfigurationChanged()`
        is invoked. Calls to view redraw or relayout methods inside this callback can
        cause poor performance on large screens. Move such work out of
        `onConfigurationChanged()`.
      """.trimIndent(),
      category = Category.CHROME_OS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    reportedCalls = HashSet()
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
      }

      override fun visitCallExpression(node: UCallExpression) {
        checkRedrawCall(context, node)
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    checkRedrawCall(context, node)
  }

  private fun checkRedrawCall(context: JavaContext, node: UCallExpression) {
    val name = node.methodName ?: return
    if (name !in REDRAW_METHODS) return
    val enclosing = UastUtils.getParentOfType(node, UMethod::class.java, true) ?: return
    if (!isOnConfigurationChanged(enclosing)) return
    val set = reportedCalls ?: return
    if (!set.add(node)) return
    val message =
      "Avoid calls to UI redraw or relayout methods inside onConfigurationChanged(); " +
        "they can degrade performance when resizing on Chrome OS and large screens."
    context.report(
      Incident(
        CHROME_OS_ON_CONFIGURATION_CHANGED,
        node,
        context.getLocation(node),
        message
      )
    )
  }

  private fun isOnConfigurationChanged(node: UMethod): Boolean {
    if (node.name != "onConfigurationChanged") return false
    val params = node.uastParameters
    return params.size == 1 && params[0].type.canonicalText == "android.content.res.Configuration"
  }
}