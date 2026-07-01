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
        private const val SIMPLE_DATE_FORMAT = "java.text.SimpleDateFormat"
        private const val DATE_FORMAT = "java.text.DateFormat"
        private const val LOCALE_TYPE = "java.util.Locale"

        private val IMPLEMENTATION = Implementation(
            DateFormatDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance of this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where you pass in an explicit locale, such as Locale.US, or use one of the get instance methods, or suppress this error if really know what you are doing.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf(SIMPLE_DATE_FORMAT)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (hasLocaleParameter(constructor)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "To format a date for machine processing, pass a Locale such as Locale.US; " +
                    "to display a date to a user, use DateFormat.getDateInstance(), " +
                    "DateFormat.getTimeInstance() or DateFormat.getDateTimeInstance() instead.",
        )
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.name != "getInstance" ||
            !context.evaluator.isMemberInClass(method, DATE_FORMAT)
        ) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "DateFormat.getInstance() uses the default locale; use getDateInstance(), " +
                    "getTimeInstance() or getDateTimeInstance() instead.",
        )
    }

    private fun hasLocaleParameter(method: PsiMethod): Boolean {
        for (parameter in method.parameterList.parameters) {
            if (parameter.type?.canonicalText == LOCALE_TYPE) {
                return true
            }
        }
        return false
    }
}