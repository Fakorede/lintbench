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
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val CHROME_OS_ON_CONFIGURATION_CHANGED =
      Issue.create(
        id = "ChromeOsOnConfigurationChanged",
        briefDescription = "Avoid UI redraw logic inside onConfigurationChanged()",
        explanation =
          """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()`
                method contains any code that can cause a redraw, your app might take a
                performance hit on large screens. To fix the issue, ensure your
                `onConfigurationChanged()` method does not contain any calls to UI redraw
                logic for specific elements.
            """,
        category = Category.CHROME_OS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val REDRAW_METHODS =
      setOf(
        "invalidate",
        "postInvalidate",
        "requestLayout",
        "forceLayout",
        "setText",
        "setHint",
        "setTextSize",
        "setTypeface",
        "setLayoutParams",
        "setPadding",
        "setBackground",
        "setBackgroundColor",
        "setBackgroundResource",
        "setImageDrawable",
        "setImageBitmap",
        "setImageResource",
        "setVisibility",
        "setAdapter",
        "notifyDataSetChanged",
        "setContentView",
        "setTitle",
        "setSupportActionBar",
        "invalidateOptionsMenu",
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): Detector.UElementHandler {
    return object : Detector.UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // No-op: parent method checks are performed in visitCallExpression.
      }

      override fun visitCallExpression(node: UCallExpression) {
        val methodName = node.methodName ?: node.methodIdentifier?.name ?: return
        if (methodName !in REDRAW_METHODS) return

        var parent: UElement? = node.uastParent
        while (parent != null) {
          if (parent is UMethod && isActivityOnConfigurationChanged(context, parent)) {
            val message =
              "Avoid calling `$methodName()` inside `onConfigurationChanged()`, " +
                "as it can trigger UI redraws and cause performance issues on large screens."
            context.report(
              Incident(CHROME_OS_ON_CONFIGURATION_CHANGED, node, context.getLocation(node), message)
            )
            return
          }
          parent = parent.uastParent
        }
      }
    }
  }

  override fun getApplicableMethodNames(): List<String>? = null

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // Detection is handled by createUastHandler/visitCallExpression.
  }

  private fun isActivityOnConfigurationChanged(context: JavaContext, method: UMethod): Boolean {
    val psi = method.javaPsi ?: return false
    if (psi.name != "onConfigurationChanged") return false

    val params = psi.parameterList.parameters
    if (params.size != 1 || params[0].type.canonicalText != "android.content.res.Configuration") {
      return false
    }

    val containingClass = psi.containingClass ?: return false
    return context.evaluator.extendsClass(containingClass, "android.app.Activity", false)
  }
}