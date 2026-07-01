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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.tryResolve

class DateFormatDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UCallExpression>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isConstructorCall()) return

                val method = node.tryResolve() as? PsiMethod ?: return
                val containingClass = method.containingClass ?: return

                if (containingClass.qualifiedName != "java.text.SimpleDateFormat") return

                val hasLocaleParameter = method.parameterList.parameters.any { param ->
                    param.type.canonicalText.removeSuffix("?") == "java.util.Locale"
                }

                if (!hasLocaleParameter) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Implied locale in date format"
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
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`,  or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat  suitable for the user's locale. The main reason you'd create an instance  this class directly is because you need to format/parse a specific  machine-readable format, in which case you almost certainly want to  explicitly ask for US to ensure that you get ASCII digits (rather than,  say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat  constructor where you pass in an explicit locale, such as Locale.US, or  use one of the get instance methods, or suppress this error if really know  what you are doing.

                Reference documentation:
                  - https://developer.android.com/reference/java/text/SimpleDateFormat.html
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}