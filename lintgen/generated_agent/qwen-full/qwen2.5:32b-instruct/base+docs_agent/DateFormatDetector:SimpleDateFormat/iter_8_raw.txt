package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.*

class DateFormatDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val IMPLICIT_LOCALE_DATE_FORMAT = Issue.create(
            id = "ImplicitLocaleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()` or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance of this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat constructor where you pass in an explicit locale, such as Locale.US, or use one of the getInstance methods.
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

    override fun visitMethodCall(context: UastLintContext, node: UCallExpression) {
        if (!isImplicitLocaleConstructor(node)) return

        val issue = IMPLICIT_LOCALE_DATE_FORMAT
        context.report(
            issue,
            node,
            context.getLocation(node),
            "Use an explicit locale or getInstance methods for SimpleDateFormat"
        )
    }

    @VisibleForTesting
    fun isImplicitLocaleConstructor(call: UCallExpression): Boolean {
        val arguments = call.valueArguments
        return arguments.size == 1 || (arguments.size == 2 && !UastUtils.getJavaPsi(arguments[1])?.typeElement?.qualifiedName.equals(Locale::class.java.name))
    }
}