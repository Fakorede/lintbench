package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        // Methods that change locale/configuration at runtime
        private val LOCALE_CHANGE_METHODS = setOf(
            "setLocale",
            "setLanguage",
            "setDefault"
        )

        // Configuration update methods
        private val CONFIGURATION_UPDATE_METHODS = setOf(
            "updateConfiguration",
            "applyOverrideConfiguration",
            "createConfigurationContext"
        )

        private const val LOCALE_CLASS = "java.util.Locale"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val CONTEXT_WRAPPER_CLASS = "android.content.ContextWrapper"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val LOCALE_LIST_COMPAT_CLASS = "androidx.core.os.LocaleListCompat"
    }

    override fun getApplicableMethodNames(): List<String> {
        return (LOCALE_CHANGE_METHODS + CONFIGURATION_UPDATE_METHODS + listOf(
            "setApplicationLocales",
            "setLocales",
            "forLanguageTag",
            "getDefault"
        )).toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        val shouldReport = when {
            // Locale.setDefault(...) - changing the default locale
            methodName == "setDefault" && containingClass == LOCALE_CLASS -> true

            // Resources.updateConfiguration(...) - deprecated but still used
            methodName == "updateConfiguration" && isSubclassOf(context, containingClass, RESOURCES_CLASS) -> true

            // Context.createConfigurationContext(...) with locale changes
            methodName == "createConfigurationContext" && isSubclassOf(context, containingClass, CONTEXT_CLASS) -> true

            // ContextWrapper.applyOverrideConfiguration(...)
            methodName == "applyOverrideConfiguration" && isSubclassOf(context, containingClass, CONTEXT_WRAPPER_CLASS) -> true

            // AppCompatDelegate.setApplicationLocales(...)
            methodName == "setApplicationLocales" && containingClass == APP_COMPAT_DELEGATE_CLASS -> true

            // Configuration.setLocale / setLocales
            (methodName == "setLocale" || methodName == "setLocales") &&
                    isSubclassOf(context, containingClass, CONFIGURATION_CLASS) -> true

            else -> false
        }

        if (shouldReport) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found a locale change at runtime. Ensure the app bundle is configured " +
                        "to not split by locale, or use the Play Core library to download " +
                        "additional locales at runtime. See " +
                        "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
        }
    }

    private fun isSubclassOf(context: JavaContext, className: String, superClassName: String): Boolean {
        if (className == superClassName) return true
        val evaluator = context.evaluator
        val psiClass = evaluator.findClass(className) ?: return false
        val superClass = evaluator.findClass(superClassName) ?: return false
        return evaluator.extendsClass(psiClass, superClass.qualifiedName ?: return false, true)
    }
}