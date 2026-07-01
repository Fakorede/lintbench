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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private data class ProjectState(
        var hasLocaleChange: Boolean = false,
        var hasSplitDisabled: Boolean = false,
        var usesPlayCore: Boolean = false,
        var localeChangeLocation: Location? = null
    )

    private val projectStates = mutableMapOf<Project, ProjectState>()

    private fun getState(project: Project): ProjectState {
        return projectStates.getOrPut(project) { ProjectState() }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setDefault", "setLocale", "setApplicationLocales")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isLocaleChange = when (method.name) {
            "setDefault" -> containingClass == "java.util.Locale" && node.valueArguments.size == 1
            "setLocale" -> containingClass == "android.content.res.Configuration" && node.valueArguments.size == 1
            "setApplicationLocales" -> containingClass == "androidx.appcompat.app.AppCompatDelegate"
            else -> false
        }

        if (isLocaleChange) {
            val state = getState(context.project)
            if (!state.hasLocaleChange) {
                state.hasLocaleChange = true
                state.localeChangeLocation = context.getLocation(node)
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("SplitInstallManager", "SplitInstallRequest")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, target: PsiElement) {
        val qualifiedName = (target as? PsiClass)?.qualifiedName
        if (qualifiedName != null && qualifiedName.startsWith("com.google.android.play.core.splitinstall")) {
            getState(context.project).usesPlayCore = true
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
            getState(context.project).hasSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val state = projectStates[context.project] ?: return
        if (state.hasLocaleChange && !state.hasSplitDisabled && !state.usesPlayCore) {
            val location = state.localeChangeLocation ?: context.getLocation(context.project.dir)
            val incident = Incident(
                ISSUE,
                null,
                location,
                "Runtime locale changes detected without disabling language splits or using Play Core"
            )
            context.client.report(context, incident)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        for ((project, state) in projectStates) {
            if (state.hasLocaleChange && !state.hasSplitDisabled && !state.usesPlayCore) {
                val location = state.localeChangeLocation ?: context.getLocation(project.dir)
                val incident = Incident(
                    ISSUE,
                    null,
                    location,
                    "Runtime locale changes detected without disabling language splits or using Play Core"
                )
                context.client.report(context, incident)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core " +
                "library must be used to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )
    }
}