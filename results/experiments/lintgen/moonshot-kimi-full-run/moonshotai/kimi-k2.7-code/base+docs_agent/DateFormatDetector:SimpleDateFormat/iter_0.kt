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
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`,
                or `getTimeInstance()` to obtain a `SimpleDateFormat` instance suitable for
                the user's locale.

                If you need to format or parse a specific machine-readable format, create
                `SimpleDateFormat` with an explicit `Locale` (for example `Locale.US`) so
                that you get ASCII digits rather than locale-specific digits.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val hasLocale = constructor.parameterList.parameters.any { param ->
            context.evaluator.typeMatches(param.type, "java.util.Locale")
        }

        if (!hasLocale) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Implicit locale used in SimpleDateFormat: add an explicit Locale " +
                    "(for example Locale.US) or use DateFormat.getDateInstance(), " +
                    "getDateTimeInstance(), or getTimeInstance()"
            )
        }
    }
}