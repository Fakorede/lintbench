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
import org.jetbrains.uast.UExpression

class DateFormatDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val SIMPLE_DATE_FORMAT =
      Issue.create(
        id = "SimpleDateFormat",
        briefDescription = "Implied locale in date format",
        explanation =
          """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made instance of `SimpleDateFormat` suitable for the user's locale.

                The main reason you'd create an instance of this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for `Locale.US` to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the `SimpleDateFormat` constructor where you pass in an explicit locale, such as `Locale.US`, or use one of the get instance methods, or suppress this error if you really know what you are doing.
            """
            .trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableConstructorTypes(): List<String> = listOf("java.text.SimpleDateFormat")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val args = node.valueArguments
    when (args.size) {
      1 -> report(context, node)
      2 -> {
        val localeArg = args[1]
        if (isLocaleGetDefault(context, localeArg)) {
          report(context, node)
        }
      }
      else -> {
        // Other constructors are not the common pattern/locale forms.
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> = emptyList()

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // No method-call checks are required for this issue.
  }

  private fun isLocaleGetDefault(context: JavaContext, expression: UExpression): Boolean {
    val call = expression as? UCallExpression ?: return false
    if (call.methodName != "getDefault") return false
    val resolved = call.resolve() ?: return false
    return context.evaluator.isMemberInClass(resolved, "java.util.Locale")
  }

  private fun report(context: JavaContext, node: UCallExpression) {
    val message =
      "Implied locale in date format. Use SimpleDateFormat(String, Locale.US) for machine-readable formats, " +
        "or DateFormat.getDateInstance()/getTimeInstance()/getDateTimeInstance() for user display."
    context.report(Incident(SIMPLE_DATE_FORMAT, node, context.getLocation(node), message))
  }
}