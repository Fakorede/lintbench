package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.android.tools.lint.detector.api.UastScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastCallKind

class DateFormatDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.CONSTRUCTOR_CALL) return

                val method = node.resolve() ?: return
                val cls = method.containingClass ?: return
                if (cls.qualifiedName != "java.text.SimpleDateFormat") return

                val hasLocaleParameter = method.parameterList.parameters.any { param ->
                    param.type.canonicalText == "java.util.Locale"
                }

                if (!hasLocaleParameter) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Implicitly using the default locale is a common source of bugs: Use `getDateInstance()`, `getDateTimeInstance()`, or `getTimeInstance()` instead, or pass an explicit locale (e.g., `Locale.US`) to this constructor."
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}