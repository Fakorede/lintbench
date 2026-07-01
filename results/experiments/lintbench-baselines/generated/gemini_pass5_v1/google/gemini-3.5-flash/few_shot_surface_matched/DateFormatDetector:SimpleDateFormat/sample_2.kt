package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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
    val DATE_FORMAT =
      Issue.create(
        id = "SimpleDateFormat",
        briefDescription = "Implied locale in date format",
        explanation =
          """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where you pass in an explicit locale, such as Locale.US, or use one of the get instance methods, or suppress this error if really know what you are doing.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    @JvmField
    val LOCALE =
      Issue.create(
        id = "DefaultLocale",
        briefDescription = "Implied default locale in case conversion or formatting",
        explanation =
          """
                Calling `String#toLowerCase()` or `String#toUpperCase()` without specifying an explicit locale is a common source of bugs. The reason for this is that those methods will use the user's current locale on the device, and if the device is set to a locale like Turkish, the rules for converting characters are different. For example, in Turkish, the uppercase equivalent of 'i' is not 'I', but 'İ' (capital I with a dot).

                Similarly, `String#format()` without a locale will use the default locale, which means that numbers may be formatted using non-ASCII digits, decimal points may be commas, etc.

                To fix this, you should either pass in a locale explicitly, such as `Locale.US` or `Locale.getDefault()`, or use one of the String methods that does not depend on locale (such as `String#equals` with `equalsIgnoreCase` instead of converting both to lowercase first).
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("java.text.SimpleDateFormat")
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val argumentCount = node.valueArguments.size
    if (argumentCount == 0 || argumentCount == 1) {
      context.report(
        DATE_FORMAT,
        node,
        context.getLocation(node),
        "To get local formatting use `DateFormat.getDateTimeInstance()`, `getDateInstance()`, or `getTimeInstance()`, or use `new SimpleDateFormat(String, Locale)` with an explicit Locale",
      )
    } else if (argumentCount == 2) {
      val secondArgType = node.valueArguments[1].getExpressionType()
      if (secondArgType != null && secondArgType.canonicalText != "java.util.Locale") {
        context.report(
          DATE_FORMAT,
          node,
          context.getLocation(node),
          "To get local formatting use `DateFormat.getDateTimeInstance()`, `getDateInstance()`, or `getTimeInstance()`, or use `new SimpleDateFormat(String, Locale)` with an explicit Locale",
        )
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("toLowerCase", "toUpperCase", "format")
  }

  override fun visitMethodCall(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
  ) {
    val name = method.name
    val evaluator = context.evaluator
    if (name == "toLowerCase" || name == "toUpperCase") {
      if (evaluator.isMemberInClass(method, "java.lang.String")) {
        if (node.valueArguments.isEmpty()) {
          context.report(
            LOCALE,
            node,
            context.getLocation(node),
            "Implicitly using the default locale is a common source of bugs: use `$name(Locale)` instead",
          )
        }
      }
    } else if (name == "format") {
      if (
        evaluator.isMemberInClass(method, "java.lang.String") ||
          evaluator.isMemberInClass(method, "java.util.Formatter")
      ) {
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
          val firstArgType = arguments[0].getExpressionType()
          if (firstArgType != null && firstArgType.canonicalText != "java.util.Locale") {
            context.report(
              LOCALE,
              node,
              context.getLocation(node),
              "Implicitly using the default locale is a common source of bugs: use `String.format(Locale, ...)` instead",
            )
          }
        }
      }
    }
  }
}