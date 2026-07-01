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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UastCallKind

class DateFormatDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.CONSTRUCTOR_CALL) return

                val method: PsiMethod = node.resolve() ?: return
                val qualifiedName = method.containingClass?.qualifiedName ?: return

                if (qualifiedName != "java.text.SimpleDateFormat") return

                val hasLocale = method.parameterList.parameters.any { param ->
                    param.type.canonicalText == "java.util.Locale"
                }

                if (!hasLocale) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Implied locale in date format"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "SimpleDateFormat",
            "Implied locale in date format",
            "Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, " +
                    "or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat " +
                    "suitable for the user's locale. The main reason you'd create an instance " +
                    "this class directly is because you need to format/parse a specific " +
                    "machine-readable format, in which case you almost certainly want to " +
                    "explicitly ask for US to ensure that you get ASCII digits (rather than, " +
                    "say, Arabic digits).\n" +
                    "\n" +
                    "Therefore, you should either use the form of the SimpleDateFormat " +
                    "constructor where you pass in an explicit locale, such as Locale.US, or " +
                    "use one of the get instance methods, or suppress this error if really know " +
                    "what you are doing.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}