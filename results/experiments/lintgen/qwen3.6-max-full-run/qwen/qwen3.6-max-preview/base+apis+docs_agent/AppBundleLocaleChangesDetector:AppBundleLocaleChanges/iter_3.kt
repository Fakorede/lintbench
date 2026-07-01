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
        "updateConfiguration",
        "setLocale",
        "setLocales",
        "setApplicationLocales"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val cls = method.containingClass
        val qualifiedName = cls?.qualifiedName ?: cls?.name

        val isRelevant = when {
            qualifiedName?.endsWith("Resources") == true && methodName == "updateConfiguration" -> true
            qualifiedName?.endsWith("Configuration") == true && (methodName == "setLocale" || methodName == "setLocales") -> true
            qualifiedName?.endsWith("AppCompatDelegate") == true && methodName == "setApplicationLocales" -> true
            qualifiedName?.endsWith("LocaleManager") == true && methodName == "setApplicationLocales" -> true
            qualifiedName?.endsWith("LocaleManagerCompat") == true && methodName == "setApplicationLocales" -> true
            else -> false
        }

        if (isRelevant) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "When changing locales at runtime, the Android App Bundle must be configured " +
                    "to not split by locale or the Play Core library must be used to download " +
                    "additional locales at runtime."
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

                Example:
                ```java
                import android.content.res.Configuration;
                import java.util.Locale;

                Configuration config = new Configuration();
                config.setLocale(new Locale("en"));
                ```

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