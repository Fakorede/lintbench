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
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setLocale",
        "setLocales",
        "updateConfiguration",
        "setApplicationLocales",
        "setDefault"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        val isLocaleChange = when (qualifiedName) {
            "android.content.res.Configuration" -> methodName == "setLocale" || methodName == "setLocales"
            "android.content.res.Resources" -> methodName == "updateConfiguration"
            "androidx.appcompat.app.AppCompatDelegate" -> methodName == "setApplicationLocales"
            "android.app.LocaleManager" -> methodName == "setApplicationLocales"
            "java.util.Locale" -> methodName == "setDefault"
            else -> false
        }

        if (isLocaleChange) {
            context.report(
                ISSUE,
                node,
                "When changing locales at runtime, the App Bundle must be configured to not split by locale " +
                    "(`android.bundle.language.enableSplit = false`) or the Play Core library must be used to download " +
                    "additional locales at runtime."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }
}