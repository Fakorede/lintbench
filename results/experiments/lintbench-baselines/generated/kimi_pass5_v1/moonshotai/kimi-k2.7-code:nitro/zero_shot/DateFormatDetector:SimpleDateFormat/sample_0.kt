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

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("java.text.SimpleDateFormat")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val parameters = constructor.parameterList.parameters
        if (parameters.isEmpty()) {
            reportIssue(context, node)
            return
        }

        if (parameters.any { it.type.canonicalText == "java.util.Locale" }) {
            return
        }

        reportIssue(context, node)
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Date format is locale dependent. Use `Locale.US` or `DateFormat.get*Instance()` instead."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or \
                `getTimeInstance()` to get a ready-made instance of `SimpleDateFormat` suitable \
                for the user's locale.

                The main reason to create an instance of this class directly is to format/parse \
                a specific machine-readable format, in which case you almost certainly want to \
                explicitly use `Locale.US` to ensure that you get ASCII digits.

                Use a constructor that accepts an explicit `Locale`, or one of the static \
                `get*Instance()` factory methods, or suppress this warning if you are certain \
                the default locale is appropriate.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
            category = Category.I18N,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}