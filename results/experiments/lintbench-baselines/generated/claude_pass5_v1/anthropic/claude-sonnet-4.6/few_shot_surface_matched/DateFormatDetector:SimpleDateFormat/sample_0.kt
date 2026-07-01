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
        private const val DATE_FORMAT_CLASS = "java.text.DateFormat"

        @JvmField
        val DATE_FORMAT = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or \
                `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable \
                for the user's locale. The main reason you'd create an instance this class \
                directly is because you need to format/parse a specific machine-readable format, \
                in which case you almost certainly want to explicitly ask for US to ensure that \
                you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor \
                where you pass in an explicit locale, such as `Locale.US`, or use one of the \
                `get instance` methods, or suppress this error if really know what you are doing.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
            androidSpecific = false
        )

        private val GET_INSTANCE_METHODS = listOf(
            "getDateInstance",
            "getDateTimeInstance",
            "getTimeInstance"
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SIMPLE_DATE_FORMAT)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // If no arguments, it's SimpleDateFormat() — no locale, flag it
        // If one argument (pattern), it's SimpleDateFormat(String) — no locale, flag it
        // If two arguments (pattern, locale) or (pattern, DateFormatSymbols), it's fine
        val args = node.valueArguments
        if (args.size >= 2) {
            // Check if second argument is a Locale
            val secondArgType = args[1].getExpressionType()?.canonicalText ?: return
            if (secondArgType == "java.util.Locale") {
                // Locale provided — no warning
                return
            }
            // If second arg is DateFormatSymbols, still no locale — flag it
        }

        val message = "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, " +
                "or `getTimeInstance()`, or use `new SimpleDateFormat(String, Locale)` with for " +
                "example `Locale.US`; using the default locale is a common source of bugs: " +
                "Use `Locale.US` for machine-readable dates, and `Locale.getDefault()` for " +
                "human-visible dates."

        context.report(
            DATE_FORMAT,
            node,
            context.getLocation(node),
            message
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return GET_INSTANCE_METHODS
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // We only care about calls on DateFormat or SimpleDateFormat
        if (!context.evaluator.isMemberInClass(method, DATE_FORMAT_CLASS) &&
            !context.evaluator.isMemberInClass(method, SIMPLE_DATE_FORMAT)
        ) {
            return
        }

        // getDateInstance(), getDateTimeInstance(), getTimeInstance() are fine — they use locale.
        // No warning needed for these methods; they are the recommended approach.
        // This method is here to allow subclasses or future logic, but per the spec
        // these methods are the correct alternatives, so we do nothing here.
    }
}