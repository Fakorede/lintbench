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
                in which case you almost certainly want to explicitly ask for `Locale.US` to \
                ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the `SimpleDateFormat` constructor \
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
        // If the constructor takes no arguments or only a pattern (String),
        // it uses the default locale which may not be appropriate.
        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // new SimpleDateFormat() — no locale
            reportIssue(context, node)
        } else if (arguments.size == 1) {
            // new SimpleDateFormat(String pattern) — no locale
            val argType = arguments[0].getExpressionType()?.canonicalText
            if (argType == "java.lang.String") {
                reportIssue(context, node)
            }
        }
        // If there are 2+ arguments, the second is likely a Locale or DateFormatSymbols — OK
    }

    override fun getApplicableMethodNames(): List<String> {
        return GET_INSTANCE_METHODS
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // We only care about calls on DateFormat (or SimpleDateFormat) without a locale argument
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, DATE_FORMAT_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, SIMPLE_DATE_FORMAT)
        ) {
            return
        }

        val arguments = node.valueArguments
        // getDateInstance(), getDateTimeInstance(), getTimeInstance() are fine when called
        // with a locale. We flag them only if they don't include a Locale argument.
        // Signatures without locale:
        //   getDateInstance()
        //   getDateInstance(int style)
        //   getDateTimeInstance()
        //   getDateTimeInstance(int dateStyle, int timeStyle)
        //   getTimeInstance()
        //   getTimeInstance(int style)
        // Signatures with locale (OK):
        //   getDateInstance(int style, Locale locale)
        //   getDateTimeInstance(int dateStyle, int timeStyle, Locale locale)
        //   getTimeInstance(int style, Locale locale)
        val hasLocaleArgument = arguments.any { arg ->
            val typeName = arg.getExpressionType()?.canonicalText ?: return@any false
            typeName == "java.util.Locale"
        }

        if (!hasLocaleArgument) {
            reportIssue(context, node)
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        val message = "To get local-independent date formatting, use `getDateInstance()`, " +
            "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
            "`new SimpleDateFormat(String template, Locale locale)` with for example " +
            "`Locale.US` for ASCII dates."
        context.report(
            DATE_FORMAT,
            node,
            context.getLocation(node),
            message
        )
    }
}