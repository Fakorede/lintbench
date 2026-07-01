package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastExpressionUtils

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    override fun getApplicableReferenceNames() = listOf("locale", "locales")

    override fun visitReference(context: JavaContext, node: UReferenceExpression, referenced: PsiElement) {
        val name = node.referenceName ?: return
        if (name != "locale" && name != "locales") return
        val receiverType = UastExpressionUtils.getReceiverType(node)?.canonicalText ?: return
        if (receiverType == "android.content.res.Configuration" ||
            receiverType == "android.content.res.Resources" ||
            receiverType == "android.content.res.Resources.Theme"
        ) {
            recordLocaleChange(context, node)
        }
    }

    override fun getApplicableMethodNames() =
        listOf("setApplicationLocales", "setDefault", "setLocale", "startInstall")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "startInstall" -> {
                if (context.evaluator.isMemberInClass(
                        method,
                        "com.google.android.play.core.splitinstall.SplitInstallManager"
                    )
                ) {
                    projectPlayCore[context.project] = true
                }
            }
            "setApplicationLocales" -> {
                if (context.evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate") ||
                    context.evaluator.isMemberInClass(method, "android.app.LocaleManager")
                ) {
                    recordLocaleChange(context, node)
                }
            }
            "setDefault" -> {
                if (context.evaluator.isMemberInClass(method, "java.util.Locale")) {
                    recordLocaleChange(context, node)
                }
            }
            "setLocale" -> {
                if (context.evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
                    recordLocaleChange(context, node)
                }
            }
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
        statementCookie: Any,
    ) {
        if (property == "enableSplit" && (parent == "language" || parentParent == "language")) {
            if (value == "false") {
                projectSplitDisabled[context.project] = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val project = context.project
        val hasLocaleChange = projectLocaleChanges[project] == true
        val hasPlayCore = projectPlayCore[project] == true
        val hasSplitDisabled = projectSplitDisabled[project] == true
        if (!hasLocaleChange && !hasPlayCore && !hasSplitDisabled) {
            return
        }
        val map = LintMap.newBuilder().apply {
            if (hasLocaleChange) putBoolean(KEY_LOCALE_CHANGE, true)
            if (hasPlayCore) putBoolean(KEY_PLAY_CORE, true)
            if (hasSplitDisabled) putBoolean(KEY_SPLIT_DISABLED, true)
        }.build()
        context.getPartialResults(ISSUE).map = map
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val map = partialResults.map ?: return
        val localeChange = map.getBoolean(KEY_LOCALE_CHANGE) == true
        val playCore = map.getBoolean(KEY_PLAY_CORE) == true
        val splitDisabled = map.getBoolean(KEY_SPLIT_DISABLED) == true
        if (!localeChange || playCore || splitDisabled) {
            clearState()
            return
        }
        for (incidents in projectIncidents.values) {
            for (incident in incidents) {
                context.client.report(context, incident)
            }
        }
        clearState()
    }

    private fun recordLocaleChange(context: JavaContext, node: UElement) {
        val message =
            "Runtime locale change detected. " +
                "Disable App Bundle language splits or use Play Core to download language splits at runtime."
        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        projectLocaleChanges[context.project] = true
        projectIncidents.getOrPut(context.project) { mutableListOf() }.add(incident)
    }

    private fun clearState() {
        projectLocaleChanges.clear()
        projectPlayCore.clear()
        projectSplitDisabled.clear()
        projectIncidents.clear()
    }

    companion object {
        private const val KEY_LOCALE_CHANGE = "locale_change"
        private const val KEY_PLAY_CORE = "play_core"
        private const val KEY_SPLIT_DISABLED = "split_disabled"

        private val projectLocaleChanges = mutableMapOf<Project, Boolean>()
        private val projectPlayCore = mutableMapOf<Project, Boolean>()
        private val projectSplitDisabled = mutableMapOf<Project, Boolean>()
        private val projectIncidents = mutableMapOf<Project, MutableList<Incident>>()

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle runtime locale changes may be mishandled",
            explanation = """
                When changing locales at runtime (for example, to provide an in-app language switcher),
                apps distributed as Android App Bundles must either disable language splits in the
                `bundle.language { enableSplit = false }` Gradle configuration, or use the Play Core
                library to download additional language splits at runtime.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true,
        )
    }
}