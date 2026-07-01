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
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, \
                or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat \
                suitable for the user's locale. The main reason you'd create an instance \
                this class directly is because you need to format/parse a specific \
                machine-readable format, in which case you almost certainly want to \
                explicitly ask for US to ensure that you get ASCII digits (rather than, \
                say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat \
                constructor where you pass in an explicit locale, such as Locale.US, or \
                use one of the get instance methods, or suppress this error if really know \
                what you are doing.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val SIMPLE_DATE_FORMAT_CLASS = "java.text.SimpleDateFormat"
        private const val DATE_FORMAT_CLASS = "java.text.DateFormat"

        private val GET_INSTANCE_METHODS = listOf(
            "getDateInstance",
            "getDateTimeInstance",
            "getTimeInstance",
        )

        private val MESSAGE = "To get local formatting use `getDateInstance()`, " +
            "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
            "`new SimpleDateFormat(String template, Locale locale)` with for example " +
            "`Locale.US` to get ASCII digits"
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(SIMPLE_DATE_FORMAT_CLASS)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // If the constructor takes a Locale parameter, it's fine
        val parameterTypes = constructor.parameterList.parameters
        for (parameter in parameterTypes) {
            val typeCanonicalText = parameter.type.canonicalText
            if (typeCanonicalText == "java.util.Locale") {
                return
            }
        }

        // No Locale parameter found — report the issue
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = MESSAGE,
        )
    }

    override fun getApplicableMethodNames(): List<String> = GET_INSTANCE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only care about calls on DateFormat (or SimpleDateFormat) class
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName != DATE_FORMAT_CLASS && qualifiedName != SIMPLE_DATE_FORMAT_CLASS) {
            return
        }

        // If the method call includes a Locale parameter, it's fine
        val parameterTypes = method.parameterList.parameters
        for (parameter in parameterTypes) {
            val typeCanonicalText = parameter.type.canonicalText
            if (typeCanonicalText == "java.util.Locale") {
                return
            }
        }

        // No Locale parameter found — report the issue
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = MESSAGE,
        )
    }
}