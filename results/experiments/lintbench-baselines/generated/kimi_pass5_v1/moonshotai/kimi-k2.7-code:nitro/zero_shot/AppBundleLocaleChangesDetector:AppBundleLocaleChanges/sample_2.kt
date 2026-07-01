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

    override fun getApplicableMethodNames(): List<String> =
        listOf("setDefault", "createConfigurationContext", "setApplicationLocales", "setLocales")

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        when (method.name) {
            "setDefault" -> {
                if (evaluator.methodMatches(method, "java.util.Locale", true)) {
                    report(context, call)
                }
            }
            "createConfigurationContext" -> {
                if (evaluator.methodMatches(method, "android.content.Context", false)) {
                    report(context, call)
                }
            }
            "setApplicationLocales" -> {
                if (evaluator.methodMatches(method, "androidx.appcompat.app.AppCompatDelegate", true)) {
                    report(context, call)
                }
            }
            "setLocales" -> {
                if (evaluator.methodMatches(method, "androidx.core.os.LocaleListCompat", true) ||
                    evaluator.methodMatches(method, "android.os.LocaleList", true)
                ) {
                    report(context, call)
                }
            }
        }
    }

    private fun report(context: JavaContext, call: UCallExpression) {
        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "When changing locales at runtime while using Android App Bundle, you must either " +
                "configure the bundle to not split by locale (e.g. `bundleConfig { language { enableSplit = false } }`) " +
                "or use the Play Core SplitInstallManager to download additional locales at runtime."
        )
    }

    companion object {
        private const val PRIORITY = 5

        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle locale handling needed for runtime locale changes",
            explanation = """
                When changing locales at runtime (for example to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale, or the Play Core library \
                (SplitInstallManager) must be used to download additional locales at runtime. Otherwise, \
                users with language splits that are not installed may not see the new language.
            """,
            category = Category.I18N,
            priority = PRIORITY,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}