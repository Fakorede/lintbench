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
        @JvmField
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()`
                to get a ready-made instance of `SimpleDateFormat` suitable for the user's locale. The main reason
                you'd create an instance of this class directly is because you need to format/parse a specific
                machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure
                that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the `SimpleDateFormat` constructor where you pass in an
                explicit locale, such as `Locale.US`, or use one of the get instance methods, or suppress this error
                if you really know what you are doing.
            """.trimIndent(),
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableConstructorTypes(): List<String> =
        listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!hasLocaleParameter(constructor)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Implied locale in date format; use `Locale.US` or one of the get instance methods"
            )
        }
    }

    private fun hasLocaleParameter(method: PsiMethod): Boolean =
        method.parameterList.parameters.any { param ->
            param.type.canonicalText == "java.util.Locale"
        }
}