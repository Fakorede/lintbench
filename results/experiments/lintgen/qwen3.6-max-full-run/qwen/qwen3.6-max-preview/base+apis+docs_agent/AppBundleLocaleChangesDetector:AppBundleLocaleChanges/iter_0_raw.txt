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

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setLocale",
        "updateConfiguration",
        "setApplicationLocales"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val methodName = node.methodName ?: return

        val isRuntimeLocaleChange = when (methodName) {
            "setLocale" -> qualifiedName == "android.content.res.Configuration"
            "updateConfiguration" -> qualifiedName == "android.content.res.Resources"
            "setApplicationLocales" -> qualifiedName == "androidx.appcompat.app.AppCompatDelegate" ||
                                        qualifiedName == "android.app.LocaleManager"
            else -> false
        }

        if (isRuntimeLocaleChange) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Runtime locale changes require configuring App Bundle to not split by locale " +
                "(`android.bundle.language.enableSplit = false`) or using the Play Core library " +
                "to download additional locales at runtime."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                Reference documentation:
                https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}