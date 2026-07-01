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
            explanation = "Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or " +
                    "`getTimeInstance()` to get a ready-made `SimpleDateFormat` suitable for the user's locale. " +
                    "If you need to format or parse a specific machine-readable format directly, create a " +
                    "`SimpleDateFormat` with an explicit `Locale` (typically `Locale.US`) to ensure ASCII digits " +
                    "are used instead of localized digits. Suppress this warning only if you are certain the " +
                    "default locale behavior is correct for this call site.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val hasLocale = constructor.parameterList.parameters.any {
            it.type.canonicalText == "java.util.Locale"
        }
        if (!hasLocale) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Implied locale in date format: use `SimpleDateFormat(String, Locale)` or one of the `DateFormat.get*Instance()` methods",
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        // No method-call checks are needed; locale issues are caught in visitConstructor.
    }
}