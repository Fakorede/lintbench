package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle may not handle runtime locale changes correctly",
            explanation = """
                When changing locales at runtime (for example, to implement an in-app language \
                switcher), the Android App Bundle must either be configured to disable locale \
                splits or the Play Core library must be used to download additional language \
                splits at runtime.

                Without one of these configurations, users who change the app language may see \
                resources from the wrong locale because the APK for the new locale may not be \
                installed on the device.

                To fix this, add `android.bundle.language.enableSplit = false` to your \
                `build.gradle` file, or use the Play Core library to fetch language splits \
                dynamically.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )

        private val LOCALE_CHANGE_METHODS = setOf(
            "setDefault",
            "setLocale",
            "updateConfiguration",
            "createConfigurationContext",
            "setApplicationLocales"
        )

        private val DEPENDENCY_CONFIGURATIONS = setOf(
            "implementation",
            "api",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "testCompileOnly",
            "testRuntimeOnly",
            "androidTestImplementation",
            "androidTestCompileOnly",
            "androidTestRuntimeOnly"
        )

        private const val PLAY_CORE_GROUP = "com.google.android.play"
    }

    private var hasRuntimeLocaleChange = false
    private var localeChangeLocation: Location? = null
    private var languageSplitEnabled: Boolean? = null
    private var hasPlayCoreLibrary = false

    override fun beforeCheckEachProject(context: Context) {
        hasRuntimeLocaleChange = false
        localeChangeLocation = null
        languageSplitEnabled = null
        hasPlayCoreLibrary = false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in LOCALE_CHANGE_METHODS) return

                val method = node.resolve()
                val containingClass = method?.containingClass?.qualifiedName
                val receiverText = node.receiver?.asSourceString() ?: ""

                val isLocaleChange = when (methodName) {
                    "setDefault" -> containingClass == "java.util.Locale" || receiverText == "Locale" || receiverText == "java.util.Locale"
                    "setLocale" -> containingClass == "android.content.res.Configuration" || receiverText == "Configuration" || receiverText == "android.content.res.Configuration"
                    "updateConfiguration" -> containingClass == "android.content.res.Resources" || receiverText == "Resources" || receiverText == "getResources()"
                    "createConfigurationContext" -> containingClass == "android.content.Context" || receiverText == "Context" || receiverText == "this"
                    "setApplicationLocales" -> containingClass == "androidx.appcompat.app.AppCompatDelegate" || receiverText == "AppCompatDelegate" || receiverText == "androidx.appcompat.app.AppCompatDelegate"
                    else -> false
                }

                if (isLocaleChange) {
                    recordLocaleChange(context, node)
                }
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        hasRuntimeLocaleChange = true
        if (localeChangeLocation == null) {
            localeChangeLocation = context.getLocation(node)
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            languageSplitEnabled = value.toBooleanStrictOrNull() ?: true
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
        if (statement == "enableSplit" && parent == "language" && parentParent == "bundle") {
            languageSplitEnabled = unnamedArguments.firstOrNull()?.toBooleanStrictOrNull() ?: true
            return
        }

        if (parent != "dependencies") return
        if (statement !in DEPENDENCY_CONFIGURATIONS) return

        for (arg in unnamedArguments) {
            if (isPlayCoreDependency(arg)) {
                hasPlayCoreLibrary = true
                return
            }
        }
        for (arg in namedArguments.values) {
            if (isPlayCoreDependency(arg)) {
                hasPlayCoreLibrary = true
                return
            }
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

private fun isPlayCoreDependency(arg: String): Boolean {
    return arg.trim().removeSurrounding("\"").removeSurrounding("'").startsWith("com.google.android.play")
}