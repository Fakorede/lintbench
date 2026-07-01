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
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private data class ProjectState(
        val localeChangeLocations: MutableList<Incident> = mutableListOf(),
        var hasLanguageSplitDisabled: Boolean = false,
        var hasPlayCore: Boolean = false
    )

    private val projectStates = java.util.concurrent.ConcurrentHashMap<String, ProjectState>()

    private fun getState(project: Project): ProjectState =
        projectStates.getOrPut(project.dir.path) { ProjectState() }

    override fun getApplicableReferenceNames(): List<String> =
        listOf("Locale", "Configuration", "AppCompatDelegate", "LocaleListCompat")

    override fun getApplicableMethodNames(): List<String> =
        listOf("setDefault", "setLocale", "setApplicationLocales", "forLanguageTags")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = context.evaluator.getQualifiedName(method) ?: return
        val isLocaleChange = qualifiedName == "java.util.Locale.setDefault" ||
                qualifiedName == "android.content.res.Configuration.setLocale" ||
                qualifiedName == "androidx.appcompat.app.AppCompatDelegate.setApplicationLocales" ||
                qualifiedName == "androidx.core.os.LocaleListCompat.forLanguageTags"

        if (isLocaleChange) {
            val state = getState(context.project)
            val incident = Incident(
                ISSUE, node,
                context.getLocation(node),
                "Runtime locale change detected. Ensure App Bundle language splits are disabled or Play Core is used."
            )
            state.localeChangeLocations.add(incident)
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, resolved: UElement) {
        val name = reference.asRenderString()
        if (name in getApplicableReferenceNames()) {
            getState(context.project)
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
        if (property == "enableSplit" && value == "false" && parent == "language" && parentParent == "bundle") {
            getState(context.project).hasLanguageSplitDisabled = true
        }
        if (value.contains("com.google.android.play:core")) {
            getState(context.project).hasPlayCore = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val state = projectStates.remove(context.project.dir.path) ?: return
        if (state.localeChangeLocations.isNotEmpty() && !state.hasLanguageSplitDisabled && !state.hasPlayCore) {
            for (incident in state.localeChangeLocations) {
                context.client.report(context, incident)
            }
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        super.checkPartialResults(context, partialResults)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime, the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true
        )
    }
}