package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DateFormatDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod?
    ) {
        // The no-arg and single-arg constructors use the default locale.
        // The two-arg constructor is fine when it passes an explicit Locale,
        // and the DateFormatSymbols variant is treated as explicitly provided.
        if (node.valueArgumentCount <= 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Implied locale in date format: use `Locale.US` or one of the `getDateInstance`/`getTimeInstance`/`getDateTimeInstance` factory methods instead."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all date formatting code should specify an explicit locale, typically
                `Locale.US`, or use one of the `DateFormat.getDateInstance()`,
                `DateFormat.getTimeInstance()`, or `DateFormat.getDateTimeInstance()` factory
                methods which automatically use the user's locale.

                The `SimpleDateFormat()` and `SimpleDateFormat(String)` constructors use the
                default device locale, which can produce unexpected localized digits or month
                names. If you are parsing or emitting a machine-readable format, pass
                `Locale.US` explicitly.
            """,
            category = Category.I18N,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}