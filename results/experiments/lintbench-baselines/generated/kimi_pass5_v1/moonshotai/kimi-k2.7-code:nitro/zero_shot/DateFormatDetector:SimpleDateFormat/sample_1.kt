package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DateFormatDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("java.text.SimpleDateFormat")
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val parameters = constructor.parameterList.parameters
        when (parameters.size) {
            0, 1 -> reportIssue(context, node)
            2 -> {
                val secondType = parameters[1].type.canonicalText
                if (secondType != "java.util.Locale") {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Implied locale in date format"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, \
                or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat \
                suitable for the user's locale. The main reason you'd create an instance \
                of this class directly is because you need to format/parse a specific \
                machine-readable format, in which case you almost certainly want to \
                explicitly ask for US to ensure that you get ASCII digits (rather than, \
                say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat \
                constructor where you pass in an explicit locale, such as Locale.US, \
                or use one of the get instance methods, or suppress this error if you \
                really know what you are doing.
                """,
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}