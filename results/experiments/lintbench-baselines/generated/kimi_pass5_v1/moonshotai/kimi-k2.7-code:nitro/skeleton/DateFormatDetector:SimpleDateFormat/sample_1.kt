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
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`,
                or `getTimeInstance()` to obtain a ready-made `SimpleDateFormat` suitable for
                the user's locale.

                The only common reason to construct `SimpleDateFormat` directly is to format or
                parse a specific machine-readable format, in which case you almost certainly
                want to pass `Locale.US` explicitly to ensure ASCII digits.

                Either pass an explicit `Locale` to the constructor (such as `Locale.US`), use
                one of the factory methods, or suppress this warning if you know what you are
                doing.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("java.text.SimpleDateFormat")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList
        if (parameters.parametersCount == 2) {
            val second = parameters.parameters[1]
            if (second.type.canonicalText == "java.util.Locale") {
                return
            }
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Implied locale in date format",
        )
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