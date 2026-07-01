package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.gradle.NamedValue
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var hasRuntimeLocaleChange = false
    private var localeChangeLocation: Location? = null
    private var languageSplitEnabled: Boolean? = null
    private var hasPlayCoreLibrary = false

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle may not handle runtime locale changes correctly",
            explanation = """
                When changing locales at runtime (for example, to implement an in-app \
                language switcher), the Android App Bundle must either be configured to \
                disable locale splits or the Play Core library must be used to download \
                additional language splits at runtime.

                Without one of these configurations, users who change the app language may \
                see resources from the wrong locale because the APK for the new locale may \
                not be installed on the device.

                To fix this, add `android.bundle.language.enableSplit = false` to your \
                `build.gradle` file, or use the Play Core library to fetch language splits \
                dynamically.
            """,
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }

    override fun beforeCheckEachProject(context: Context) {
        hasRuntimeLocaleChange = false
        localeChangeLocation = null
        languageSplitEnabled = null
        hasPlayCoreLibrary = false
    }

    // SourceCodeScanner
    override fun getApplicableMethodNames(): List<String> {
        return listOf("setDefault", "updateConfiguration", "createConfigurationContext", "setApplicationLocales")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        when {
            containingClass == "java.util.Locale" && method.name == "setDefault" -> {
                recordLocaleChange(context, node)
            }
            containingClass == "android.content.res.Resources" && method.name == "updateConfiguration" -> {
                recordLocaleChange(context, node)
            }
            containingClass == "android.content.Context" && method.name == "createConfigurationContext" -> {
                recordLocaleChange(context, node)
            }
            containingClass == "androidx.appcompat.app.AppCompatDelegate" && method.name == "setApplicationLocales" -> {
                recordLocaleChange(context, node)
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        hasRuntimeLocaleChange = true
        if (localeChangeLocation == null) {
            localeChangeLocation = context.getLocation(node)
        }
    }

    // GradleScanner
    override fun checkDslPropertyCommit(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any,
        namedArguments: List<NamedValue>
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            languageSplitEnabled = value.toBooleanStrictOrNull() ?: true
        }

        val token = property + value
        if (parent == "dependencies" && token.contains("com.google.android.play")) {
            hasPlayCoreLibrary = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasRuntimeLocaleChange) return
        if (hasPlayCoreLibrary) return
        if (languageSplitEnabled == false) return

        val location = localeChangeLocation ?: return
        context.report(
            ISSUE,
            location,
            "Runtime locale changes may not work correctly with App Bundle locale splits. " +
                "Set `android.bundle.language.enableSplit = false` or include the Play Core library."
        )
    }
}