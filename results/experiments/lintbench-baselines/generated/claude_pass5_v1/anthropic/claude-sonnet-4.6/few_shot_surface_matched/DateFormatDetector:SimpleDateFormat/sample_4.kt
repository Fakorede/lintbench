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
                get instance methods, or suppress this error if really know what you are doing.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html"
        )

        private val GET_INSTANCE_METHODS = listOf(
            "getDateInstance",
            "getDateTimeInstance",
            "getTimeInstance"
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(SIMPLE_DATE_FORMAT)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // If there are no arguments, or only a pattern string argument (no Locale),
        // then this is a locale-implied usage.
        val args = node.valueArguments
        if (args.isEmpty()) {
            // new SimpleDateFormat() — no pattern, no locale
            reportIssue(context, node)
            return
        }

        // Check if any argument is a Locale
        val hasLocaleArg = args.any { arg ->
            val type = arg.getExpressionType()?.canonicalText ?: return@any false
            type == "java.util.Locale"
        }

        if (!hasLocaleArg) {
            // new SimpleDateFormat(pattern) — pattern but no locale
            reportIssue(context, node)
        }
        // If there is a Locale argument, the usage is fine — do not report
    }

    override fun getApplicableMethodNames(): List<String> = GET_INSTANCE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // We only care about calls on DateFormat (or SimpleDateFormat) class itself
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass != DATE_FORMAT_CLASS && containingClass != SIMPLE_DATE_FORMAT) {
            return
        }

        val methodName = method.name
        if (methodName !in GET_INSTANCE_METHODS) {
            return
        }

        // Check if any argument is a Locale — if so, usage is fine
        val args = node.valueArguments
        val hasLocaleArg = args.any { arg ->
            val type = arg.getExpressionType()?.canonicalText ?: return@any false
            type == "java.util.Locale"
        }

        // These factory methods are acceptable even without a Locale (they use the user's
        // locale intentionally), so we do NOT report them. The spec says to use these
        // methods as alternatives. We only report the constructor usage above.
        // However, if someone calls these with a Locale, that's also fine.
        // Per the spec, getDateInstance() etc. are the recommended alternatives,
        // so we don't flag them here.
        _ = hasLocaleArg // suppress unused warning
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            DATE_FORMAT,
            node,
            context.getLocation(node),
            "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, or " +
                "`getTimeInstance()`, or use `new SimpleDateFormat(String, Locale)` with for " +
                "example `Locale.US`; using the default locale is suspicious"
        )
    }
}