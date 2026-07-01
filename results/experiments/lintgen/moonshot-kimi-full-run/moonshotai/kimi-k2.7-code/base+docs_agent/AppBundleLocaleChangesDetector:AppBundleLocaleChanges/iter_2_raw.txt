package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

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
                Scope.JAVA_FILE,
                Scope.GRADLE_FILE
            )
        )
    }

    private val localeChangeCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var languageSplitEnabled = true
    private var hasPlayCore = false

    override fun beforeCheckRootProject(context: Context) {
        localeChangeCalls.clear()
        languageSplitEnabled = true
        hasPlayCore = false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
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
    ): Boolean {
        if (pathContains(parent, namedParent, property, "bundle", "language", "enableSplit")) {
            languageSplitEnabled = value != "false"
        } else if (isInDependencies(parent, namedParent) && value.contains("com.google.android.play:")) {
            hasPlayCore = true
        }
        return false
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String,
        namedParent: String?,
        name: String,
        arguments: List<String>,
        cookie: Any
    ) {
        if (pathContains(parent, namedParent, name, "bundle", "language", "enableSplit")) {
            val arg = arguments.firstOrNull() ?: return
            languageSplitEnabled = arg != "false"
        } else if (isInDependencies(parent, namedParent)) {
            if (arguments.any { it.contains("com.google.android.play:") }) {
                hasPlayCore = true
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!context.project.isGradleProject) return
        if (localeChangeCalls.isEmpty() || !languageSplitEnabled || hasPlayCore) return

        val message =
            "Runtime locale changes require either `bundle.language.enableSplit = false` or the Play Core library when using Android App Bundle"

        for ((javaContext, node) in localeChangeCalls) {
            javaContext.report(ISSUE, node, javaContext.getLocation(node), message)
        }

        localeChangeCalls.clear()
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

    private fun pathContains(
        parent: String,
        namedParent: String?,
        name: String?,
        vararg segments: String
    ): Boolean {
        val path = buildString {
            if (!namedParent.isNullOrEmpty()) {
                append(namedParent).append('.')
            }
            append(parent)
            if (!name.isNullOrEmpty()) {
                append('.').append(name)
            }
        }
        return segments.all { path.contains(it, ignoreCase = true) }
    }

    private fun isInDependencies(parent: String, namedParent: String?): Boolean {
        return parent == "dependencies" || namedParent == "dependencies"
    }
}