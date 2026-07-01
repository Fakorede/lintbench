package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getExpressionType

class DateFormatDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(context: JavaContext, node: UCallExpression) {
        val argCount = node.valueArgumentCount
        if (argCount == 2) {
            val secondArg: UExpression = node.valueArguments.getOrNull(1) ?: return
            val type = secondArg.getExpressionType()?.canonicalText
            if (type == "java.util.Locale") {
                return
            }
        }

        context.report(
            ISSUE,
            context.getCallLocation(node, includeReceiver = false, includeArguments = true),
            "Implied locale in date format",
            "To format or parse a machine-readable date, pass an explicit Locale (e.g., Locale.US) " +
                "to the SimpleDateFormat constructor. Otherwise use DateFormat.getDateInstance(), " +
                "getDateTimeInstance(), or getTimeInstance(), which are locale-aware."
        )
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or
                `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for
                the user's locale.

                The main reason to create an instance of this class directly is because you need to
                format/parse a specific machine-readable format, in which case you almost
                certainly want to explicitly ask for `Locale.US` to ensure that you get ASCII
                digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where
                you pass in an explicit locale, such as `Locale.US`, or use one of the get instance
                methods, or suppress this error if you really know what you are doing.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}