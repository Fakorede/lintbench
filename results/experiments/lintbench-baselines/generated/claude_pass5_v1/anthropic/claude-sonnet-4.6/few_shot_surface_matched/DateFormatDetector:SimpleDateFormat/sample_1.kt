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
                `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for \
                the user's locale. The main reason you'd create an instance this class directly is \
                because you need to format/parse a specific machine-readable format, in which case \
                you almost certainly want to explicitly ask for US to ensure that you get ASCII \
                digits (rather than, say, Arabic digits).

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
            androidSpecific = false,
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html"
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
        // If the constructor has no arguments (default constructor), it uses the default locale
        // If it has arguments, check whether a Locale is among them
        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // new SimpleDateFormat() — no pattern, no locale: flag it
            reportIssue(context, node)
            return
        }

        // Check if any argument is of type java.util.Locale
        val hasLocaleArgument = arguments.any { arg ->
            val type = arg.getExpressionType()
            type != null && context.evaluator.typeMatches(type, "java.util.Locale")
        }

        if (!hasLocaleArgument) {
            reportIssue(context, node)
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return GET_INSTANCE_METHODS
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Only care about calls on DateFormat or SimpleDateFormat
        if (!context.evaluator.isMemberInClass(method, DATE_FORMAT_CLASS) &&
            !context.evaluator.isMemberInClass(method, SIMPLE_DATE_FORMAT)
        ) {
            return
        }

        // These factory methods are fine — they are the recommended approach.
        // We do NOT flag these; this visitMethodCall is here in case we want to
        // detect misuse, but per the spec the get*Instance methods are acceptable.
        // Nothing to report here.
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        val message =
            "To get local-sensitive behavior, use `getDateInstance()`, " +
                "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
                "`new SimpleDateFormat(String, Locale)` with `Locale.US` " +
                "if the format should not be localized."
        context.report(
            DATE_FORMAT,
            node,
            context.getLocation(node),
            message
        )
    }
}