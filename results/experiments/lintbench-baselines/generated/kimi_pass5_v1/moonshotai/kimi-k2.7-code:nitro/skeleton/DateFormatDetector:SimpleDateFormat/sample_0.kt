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
        private val IMPLEMENTATION = Implementation(
            DateFormatDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                When parsing or formatting dates for the user, you should use
                `DateFormat.getDateInstance()`, `DateFormat.getDateTimeInstance()`,
                or `DateFormat.getTimeInstance()`, which create a locale-aware
                formatter.

                If you need a specific machine-readable format (for example, RFC
                3339), create a `SimpleDateFormat` with an explicit `Locale`, such
                as `Locale.US`. Calling the `SimpleDateFormat(String)` or
                `SimpleDateFormat()` constructors uses the device's default
                locale, which can produce unexpected results (for example,
                non-ASCII digits).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (hasExplicitLocale(constructor)) {
            return
        }

        val message = """
            Implied locale in date format.
            Use `new SimpleDateFormat(String, Locale)` with an explicit locale such as `Locale.US`, or use `DateFormat.getDateInstance()`, `DateFormat.getDateTimeInstance()`, or `DateFormat.getTimeInstance()`.
        """.trimIndent()

        context.report(ISSUE, node, context.getLocation(node), message)
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // No method-call checks are required.
    }

    private fun hasExplicitLocale(constructor: PsiMethod): Boolean {
        return constructor.parameterList.parameters.any { param ->
            param.type.canonicalText == "java.util.Locale"
        }
    }
}