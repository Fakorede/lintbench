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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UastCallKind

class DateFormatDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SIMPLE_DATE_FORMAT_CLS = "java.text.SimpleDateFormat"
        private const val LOCALE_CLS = "java.util.Locale"

        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance of this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where you pass in an explicit locale, such as Locale.US, or use one of the get instance methods, or suppress this error if you really know what you are doing.
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

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.kind != UastCallKind.CONSTRUCTOR_CALL) {
                return
            }

            val method = node.resolve() ?: return
            if (!context.evaluator.isMemberInClass(method, SIMPLE_DATE_FORMAT_CLS)) {
                return
            }

            if (hasLocaleParameter(method, context)) {
                return
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `new SimpleDateFormat(pattern, Locale.US)` or `DateFormat.get...Instance()` instead of relying on the default locale"
            )
        }
    }

    private fun hasLocaleParameter(method: PsiMethod, context: JavaContext): Boolean {
        return method.parameterList.parameters.any { param ->
            context.evaluator.typeMatches(param.type, LOCALE_CLS)
        }
    }
}