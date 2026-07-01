package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g., to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale " +
                "(android.bundle.language.enableSplit = false) or the Play Core library must be used " +
                "to download additional locales at runtime. Otherwise, the app may crash or fail to find " +
                "resources for the new locale on devices that do not support dynamic language resource loading.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "updateConfiguration",
            "setDefault"
        )

        private val projectStates = mutableMapOf<String, ProjectState>()

        private data class ProjectState(
            var hasLocaleChanges: Boolean = false,
            var hasSplitDisabled: Boolean = false,
            val locations: MutableList<Location> = mutableListOf()
        )

        private fun getState(context: Context): ProjectState {
            val key = context.project?.dir?.absolutePath ?: context.toString()
            return projectStates.getOrPut(key) { ProjectState() }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? = LOCALE_CHANGE_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val isLocaleChange = when (method.name) {
            "setLocale" -> qualifiedName == "android.content.res.Configuration"
            "updateConfiguration" -> qualifiedName == "android.content.res.Resources"
            "setDefault" -> qualifiedName == "java.util.Locale"
            else -> false
        }

        if (isLocaleChange) {
            val state = getState(context)
            state.hasLocaleChanges = true
            state.locations.add(context.getLocation(node))
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Reference scanning not required for this detector
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle" && value == "false") {
            getState(context).hasSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val key = context.project?.dir?.absolutePath ?: context.toString()
        val state = projectStates.remove(key) ?: return

        if (state.hasLocaleChanges && !state.hasSplitDisabled) {
            val message = "Runtime locale changes detected. When using Android App Bundles, you must either " +
                "set `android.bundle.language.enableSplit = false` in your build.gradle file or use the " +
                "Play Core library to download additional locales at runtime."
            for (location in state.locations) {
                context.report(ISSUE, location, message)
            }
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Incremental partial result merging not required for this cross-file aggregation detector
    }
}