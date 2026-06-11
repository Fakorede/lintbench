package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class DateFormatDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ISSUE_ID = "ImpliedLocaleInDateFormat"
        private const val ISSUE_NAME = "Implied locale in date format"
        private const val ISSUE_EXPLANATION =
            "Almost all callers should use `getDateInstance()`, `getDateTimeInstance()` or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale. The main reason you'd create an instance this class directly is because you need to format/parse a specific machine-readable format, in which case you almost certainly want to explicitly ask for US to ensure that you get ASCII digits (rather than, say, Arabic digits)."
        private const val ISSUE_URL = "https://developer.android.com/reference/java/text/SimpleDateFormat.html"

        val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = ISSUE_NAME,
            explanation = ISSUE_EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("java.text.SimpleDateFormat")
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val arguments = node.valueArguments
        if (arguments.size <= 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `getDateInstance()`, `getDateTimeInstance()` or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale."
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getDateInstance", "getDateTimeInstance", "getTimeInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val arguments = node.valueArguments
        if (arguments.size == 1 && arguments[0] is UElement) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `getDateInstance()`, `getDateTimeInstance()` or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale."
            )
        }
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("SimpleDateFormat")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val containingClass = reference.getContainingUClass()
        if (containingClass != null && !isLocaleExplicitlySet(containingClass)) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Use `getDateInstance()`, `getDateTimeInstance()` or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable for the user's locale."
            )
        }
    }

    private fun isLocaleExplicitlySet(containingClass: UClass): Boolean {
        val methods = containingClass.methods
        for (method in methods) {
            if (method.name == "<init>") {
                val arguments = method.uastParameters
                if (arguments.size > 1 && arguments[1].type.canonicalText == "java.util.Locale") {
                    return true
                }
            }
        }
        return false
    }

}