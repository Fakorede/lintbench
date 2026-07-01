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

class DateFormatDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "SimpleDateFormat",
        briefDescription = "Implied locale in date format",
        explanation =
          """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where you pass in an explicit locale, such as Locale.US, or use one of the get instance methods, or suppress this error if really know what you are doing.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableConstructorTypes(): List<String> = listOf("java.text.SimpleDateFormat")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val hasLocale =
      node.valueArguments.any { arg -> arg.getExpressionType()?.canonicalText == "java.util.Locale" }

    if (hasLocale) return

    val message =
      "To get local formatting use getDateInstance(), getDateTimeInstance(), or getTimeInstance(), " +
        "or use new SimpleDateFormat(String template, Locale locale) with for example Locale.US for ASCII dates."
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }

  override fun getApplicableMethodNames(): List<String> = emptyList()

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // No method calls to check for this issue
  }
}