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

class AppBundleLocaleChangesDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setDefault",
        "updateConfiguration",
        "setLocale"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val methodName = node.methodName ?: return

        val isRuntimeLocaleChange = when (methodName) {
            "setDefault" -> qualifiedName == "java.util.Locale"
            "updateConfiguration" -> qualifiedName == "android.content.res.Resources"
            "setLocale" -> qualifiedName == "android.content.res.Configuration"
            else -> false
        }

        if (isRuntimeLocaleChange) {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "When changing locales at runtime, the Android App Bundle must be configured " +
                "to not split by locale or the Play Core library must be used to download " +
                "additional locales at runtime."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "AppBundleLocaleChanges",
            "App Bundle handling of runtime locale changes",
            "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
            "the Android App Bundle must be configured to not split by locale or the Play Core " +
            "library must be used to download additional locales at runtime.\n" +
            "Reference documentation:\n" +
            "  - https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}