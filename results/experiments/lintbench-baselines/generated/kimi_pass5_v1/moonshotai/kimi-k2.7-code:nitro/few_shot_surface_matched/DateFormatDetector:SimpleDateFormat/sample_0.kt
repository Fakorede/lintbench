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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DateFormatDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val SIMPLE_DATE_FORMAT = "java.text.SimpleDateFormat"
    private const val DATE_FORMAT = "java.text.DateFormat"
    private const val LOCALE = "java.util.Locale"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "SimpleDateFormat",
        briefDescription = "Implied locale in date format",
        explanation =
          """
                To format dates for display, use `DateFormat.getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()`.

                If you need a fixed, machine-readable format (such as RFC 3339), create the `SimpleDateFormat` with an explicit locale such as `Locale.US` to avoid localized digits.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableConstructorTypes(): List<String> = listOf(SIMPLE_DATE_FORMAT)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    for (parameter in constructor.parameterList.parameters) {
      if (parameter.type.equalsToText(LOCALE)) {
        return
      }
    }

    val message =
      "Implied locale in date format: use `new SimpleDateFormat(String, Locale)` or `DateFormat.getDateInstance()`/`getDateTimeInstance()`/`getTimeInstance()` instead"
    val location = context.getLocation(node)
    context.report(Incident(ISSUE, node, location, message))
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("getDateInstance", "getDateTimeInstance", "getTimeInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, DATE_FORMAT)) {
      return
    }

    val receiverType = node.receiverType ?: node.receiver?.getExpressionType() ?: return
    if (receiverType is PsiClassType && context.evaluator.extendsClass(receiverType, SIMPLE_DATE_FORMAT, false)) {
      val message =
        "Use `${DATE_FORMAT}.${node.methodName}(...)` instead of `${SIMPLE_DATE_FORMAT}.${node.methodName}(...)`"
      val location = context.getLocation(node)
      context.report(Incident(ISSUE, node, location, message))
    }
  }
}