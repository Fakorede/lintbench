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

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SIMPLE_DATE_FORMAT)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // If there are no arguments, it's the no-arg constructor — flag it
        // If there's one argument (the pattern), it's missing a locale — flag it
        // If there are two or more arguments, the second should be a Locale — don't flag it
        val args = node.valueArguments
        if (args.size >= 2) {
            // Has a locale (or at least a second argument) — assume it's fine
            return
        }

        val message = if (args.isEmpty()) {
            "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, or " +
                "`getTimeInstance()`, or use `new SimpleDateFormat(String template, " +
                "Locale locale)` with for example `Locale.US`; avoid `SimpleDateFormat()`"
        } else {
            "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, or " +
                "`getTimeInstance()`, or use `new SimpleDateFormat(String template, " +
                "Locale locale)` with for example `Locale.US`; avoid `SimpleDateFormat(template)`"
        }

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
        // We only care about calls on SimpleDateFormat (or its class)
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName != SIMPLE_DATE_FORMAT) {
            return
        }

        // getDateInstance(), getDateTimeInstance(), getTimeInstance() without a Locale argument
        // Check if any argument is a Locale
        val args = node.valueArguments
        val hasLocale = args.any { arg ->
            val type = arg.getExpressionType()?.canonicalText ?: return@any false
            type == "java.util.Locale"
        }

        if (!hasLocale) {
            val methodName = method.name
            context.report(
                DATE_FORMAT,
                node,
                context.getLocation(node),
                "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, or " +
                    "`getTimeInstance()` with a `Locale` argument; avoid `$methodName()` without " +
                    "a locale"
            )
        }
    }
}