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

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val LOCALE_MANAGER_COMPAT_CLASS = "androidx.core.app.LocaleManagerCompat"
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"
        private const val CONTEXT_CLASS = "android.content.Context"

        // Map of method name -> list of qualifying class names
        private val METHOD_TO_CLASSES: Map<String, List<String>> = mapOf(
            "setLocale" to listOf(CONFIGURATION_CLASS),
            "setLocales" to listOf(CONFIGURATION_CLASS),
            "updateConfiguration" to listOf(RESOURCES_CLASS),
            "createConfigurationContext" to listOf(CONTEXT_CLASS),
            "applyOverrideConfiguration" to listOf(CONTEXT_CLASS),
            "setApplicationLocales" to listOf(APP_COMPAT_DELEGATE_CLASS, LOCALE_MANAGER_COMPAT_CLASS, LOCALE_MANAGER_CLASS)
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return METHOD_TO_CLASSES.keys.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val expectedClasses = METHOD_TO_CLASSES[methodName] ?: return
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        val matches = expectedClasses.any { expectedClass ->
            evaluator.extendsClass(containingClass, expectedClass, true) ||
                    evaluator.implementsInterface(containingClass, expectedClass, true)
        }

        if (matches) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found a locale change using `$methodName`; if using App Bundles, the base " +
                        "module must be configured to not split by locale, or the Play Core " +
                        "library must be used to download additional locales at runtime."
            )
        }
    }
}