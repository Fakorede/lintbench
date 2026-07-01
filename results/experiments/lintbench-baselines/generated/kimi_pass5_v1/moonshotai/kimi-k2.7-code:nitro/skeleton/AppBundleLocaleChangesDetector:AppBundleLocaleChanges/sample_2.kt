package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
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
        private const val LOCALE_CHANGE_PREFIX = "localeChange:"
        private const val LANGUAGE_SPLIT_PREFIX = "languageSplit:"

        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "Apps using Android App Bundles may split language resources by locale. " +
                "If the app changes the user's locale at runtime (for example, with an in-app language switcher), " +
                "the new locale's resources might not be installed. To avoid missing resources, either " +
                "disable language splitting by setting `android.bundle.language.enableSplit = false` in the " +
                "Gradle build file, or use the Play Core library to download the required language split at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // No reference-based checks are required.
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("setDefault", "setApplicationLocales")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val className = when (method.name) {
            "setDefault" -> "java.util.Locale"
            "setApplicationLocales" -> "androidx.appcompat.app.AppCompatDelegate"
            else -> return
        }

        if (!context.evaluator.isMemberInClass(method, className)) {
            return
        }

        val key = "$LOCALE_CHANGE_PREFIX${context.file.path}:${node.sourcePsi?.textOffset ?: node.hashCode()}"
        context.getPartialResults(ISSUE).accept(
            key,
            method.name,
            context.getLocation(node),
        )
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        if (property != "enableSplit" || parent != "language" || parentParent != "bundle") {
            return
        }

        val key = "$LANGUAGE_SPLIT_PREFIX${context.file.path}"
        context.getPartialResults(ISSUE).accept(
            key,
            value,
            context.getLocation(valueCookie),
        )
    }

    override fun afterCheckEachProject(context: Context) {
        // Cross-file analysis is performed in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val localeKeys = partialResults.keys().filter { it.startsWith(LOCALE_CHANGE_PREFIX) }
        if (localeKeys.isEmpty()) {
            return
        }

        val splitsDisabled = partialResults.keys().any {
            it.startsWith(LANGUAGE_SPLIT_PREFIX) && partialResults.get(it) == "false"
        }
        if (splitsDisabled) {
            return
        }

        val message =
            "When changing locales at runtime with Android App Bundles, you must either set " +
                "`android.bundle.language.enableSplit = false` in your Gradle build file or use " +
                "the Play Core library to download additional locales at runtime."

        for (key in localeKeys) {
            val location = partialResults.getLocation(key) ?: continue
            context.report(ISSUE, location, message)
        }
    }
}