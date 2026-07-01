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
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                When formatting dates for display, use `DateFormat.getDateInstance()`, \
                `DateFormat.getTimeInstance()`, or `DateFormat.getDateTimeInstance()`, which \
                return a formatter appropriate for the user's locale.

                If you need a fixed, machine-readable format (for example, to parse a network \
                response), construct `SimpleDateFormat` with an explicit `Locale`—typically \
                `Locale.US`—so that you get ASCII digits and English month/day names regardless \
                of the user's locale.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        if (parameters.size == 1 && parameters[0].type.canonicalText == "java.lang.String") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Implied locale in date format: use Locale.US or one of the DateFormat.get*Instance() methods",
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // No method calls are flagged by this detector.
    }
}