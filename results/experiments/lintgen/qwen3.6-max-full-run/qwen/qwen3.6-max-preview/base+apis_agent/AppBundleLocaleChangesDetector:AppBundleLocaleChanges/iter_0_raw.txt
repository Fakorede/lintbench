package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var hasLocaleChange = false
    private var hasPlayCore = false
    private var hasGradleConfig = false
    private val localeChangeLocations = mutableListOf<Location>()

    override fun beforeCheckEachProject(context: Context) {
        hasLocaleChange = false
        hasPlayCore = false
        hasGradleConfig = false
        localeChangeLocations.clear()
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setLocale", "setLocales", "updateConfiguration", "setApplicationLocales", "setDefault", "addLanguage", "startInstall"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        when {
            qualifiedName == "android.content.res.Configuration" && (methodName == "setLocale" || methodName == "setLocales") -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "android.content.res.Resources" && methodName == "updateConfiguration" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "androidx.appcompat.app.AppCompatDelegate" && methodName == "setApplicationLocales" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "java.util.Locale" && methodName == "setDefault" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName.startsWith("com.google.android.play.core.splitinstall") -> {
                hasPlayCore = true
            }
        }
    }

    override fun visitBuildScript(context: Context, script: GradleScript) {
        val text = script.text
        if (text.contains("enableSplit")) {
            val regex = Regex("""language\s*\{[\s\S]*?enableSplit\s*(?:=\s*false|\.set\(false\))""", RegexOption.DOT_MATCHES_ALL)
            if (regex.containsMatchIn(text)) {
                hasGradleConfig = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasLocaleChange && !hasPlayCore && !hasGradleConfig) {
            val message = "When changing locales at runtime, the App Bundle must be configured to not split by locale " +
                "(`android.bundle.language.enableSplit = false`) or the Play Core library must be used to download " +
                "additional locales at runtime."
            for (location in localeChangeLocations) {
                context.report(ISSUE, location, message)
            }
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
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }
}