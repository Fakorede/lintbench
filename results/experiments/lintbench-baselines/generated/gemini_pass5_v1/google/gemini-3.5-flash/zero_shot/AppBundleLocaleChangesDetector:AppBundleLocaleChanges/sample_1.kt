package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "setLocale",
            "setLocales",
            "updateConfiguration",
            "createConfigurationContext"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = method.name

        val isTarget = when (methodName) {
            "setDefault" -> evaluator.isMemberInClass(method, "java.util.Locale")
            "setLocale", "setLocales" -> evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "updateConfiguration" -> evaluator.isMemberInSubclassOf(method, "android.content.res.Resources", false)
            "createConfigurationContext" -> evaluator.isMemberInSubclassOf(method, "android.content.Context", false)
            else -> false
        }

        if (isTarget) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found custom locale changing API. When using App Bundles, please ensure that " +
                        "language splits are disabled or that Play Core is used to split-install new locales."
            )
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("locale")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField &&
            referenced.name == "locale" &&
            context.evaluator.isMemberInClass(referenced, "android.content.res.Configuration")
        ) {
            val parent = reference.uastParent
            if (parent is UBinaryExpression) {
                val operatorText = parent.operator.text
                if ((operatorText == "=") && parent.leftOperand == reference) {
                    context.report(
                        ISSUE,
                        reference,
                        context.getLocation(reference),
                        "Setting `Configuration.locale` directly. When using App Bundles, please ensure that " +
                                "language splits are disabled or that Play Core is used to split-install new locales."
                    )
                }
            }
        }
    }
}