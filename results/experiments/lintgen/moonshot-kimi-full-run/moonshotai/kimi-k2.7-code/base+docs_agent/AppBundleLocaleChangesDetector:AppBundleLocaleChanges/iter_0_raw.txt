package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When your app changes the user's locale at runtime (for example, to implement an
                in-app language picker) and you publish it as an Android App Bundle, you must
                either disable language splits in the base module or use the Play Core library to
                download the needed language splits at runtime. Otherwise users may see resources
                in the wrong language after switching.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.GRADLE_SCOPE
            )
        )
    }

    private val localeChangeCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var languageSplitEnabled = true
    private var hasPlayCore = false

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (isLocaleChangeCall(node)) {
                    localeChangeCalls.add(context to node)
                }
            }
        }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        namedParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (property == "enableSplit" && isBundleLanguageProperty(parent, namedParent)) {
            languageSplitEnabled = value != "false"
        }
    }

    override fun checkDslPropertyContribution(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        namedParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (parent == "dependencies" && value.contains("com.google.android.play:")) {
            hasPlayCore = true
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!context.project.isGradleProject) return
        if (!languageSplitEnabled || hasPlayCore || localeChangeCalls.isEmpty()) return

        val message =
            "Runtime locale changes require either `bundle.language.enableSplit = false` or the Play Core library when using Android App Bundle"

        for ((javaContext, node) in localeChangeCalls) {
            javaContext.report(
                ISSUE,
                node,
                javaContext.getLocation(node),
                message
            )
        }
    }

    private fun isLocaleChangeCall(node: UCallExpression): Boolean {
        val method = node.resolve() ?: return false
        val methodName = method.name ?: return false
        val className = method.containingClass?.qualifiedName ?: return false

        return when (className) {
            "java.util.Locale" -> methodName == "setDefault"
            "android.content.res.Resources" -> methodName == "updateConfiguration"
            "android.content.res.Configuration" -> methodName == "setLocale" || methodName == "setLayoutDirection"
            "android.content.Context" -> methodName == "createConfigurationContext"
            "androidx.appcompat.app.AppCompatDelegate" -> methodName == "setApplicationLocales"
            else -> false
        }
    }

    private fun isBundleLanguageProperty(parent: String, namedParent: String?): Boolean {
        return (parent == "language" && namedParent == "bundle") ||
                (parent == "bundle" && namedParent == "language") ||
                parent == "bundle.language"
    }
}