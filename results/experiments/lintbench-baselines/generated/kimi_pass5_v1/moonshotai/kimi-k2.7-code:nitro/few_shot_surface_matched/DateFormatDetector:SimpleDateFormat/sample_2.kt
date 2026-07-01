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
        private const val CLS_DATE_FORMAT = "java.text.DateFormat"
        private const val CLS_SIMPLE_DATE_FORMAT = "java.text.SimpleDateFormat"
        private const val CLS_LOCALE = "java.util.Locale"

        @JvmField
        val SIMPLE_DATE_FORMAT =
            Issue.create(
                id = "SimpleDateFormat",
                briefDescription = "Implied locale in date format",
                explanation =
                    """
                    Almost all callers should use `getDateInstance()`,
                    `getDateTimeInstance()`, or `getTimeInstance()` to get a ready-made
                    instance of SimpleDateFormat suitable for the user's locale. The main
                    reason you'd create an instance of this class directly is because you
                    need to format/parse a specific machine-readable format, in which case
                    you almost certainly want to explicitly ask for US to ensure that you
                    get ASCII digits (rather than, say, Arabic digits).

                    Therefore, you should either use the form of the SimpleDateFormat
                    constructor where you pass in an explicit locale, such as Locale.US,
                    or use one of the get instance methods, or suppress this error if you
                    really know what you are doing.
                    """,
                moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(CLS_SIMPLE_DATE_FORMAT)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(constructor, CLS_SIMPLE_DATE_FORMAT)) {
            return
        }

        val args = node.valueArguments
        if (args.size == 2) {
            val secondArgType = args[1].getExpressionType()?.canonicalText
            if (secondArgType == CLS_LOCALE) {
                return
            }
        }

        val message =
            "To ensure a consistent format, pass an explicit Locale argument to the SimpleDateFormat constructor (typically Locale.US), or use DateFormat.getDateInstance(), getDateTimeInstance(), or getTimeInstance()."
        context.report(SIMPLE_DATE_FORMAT, node, context.getLocation(node), message)
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("getDateInstance", "getDateTimeInstance", "getTimeInstance", "getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // DateFormat.getDateInstance(), getDateTimeInstance(), getTimeInstance(), and
        // getInstance() are the recommended locale-aware alternatives; do not flag them.
        if (!context.evaluator.isMemberInClass(method, CLS_DATE_FORMAT)) {
            return
        }
    }
}