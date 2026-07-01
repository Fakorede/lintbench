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
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core library " +
                "must be used to download additional locales at runtime. Otherwise, the app may crash or " +
                "fail to load resources for the newly selected locale on devices that received a split APK.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val LOCALE_CHANGING_METHODS = listOf(
            "setApplicationLocales",
            "setLocale",
            "setLocales",
            "updateConfiguration"
        )

        private const val KEY_CALLS = "AppBundleLocaleChangesDetector.calls"
        private const val KEY_SPLIT_DISABLED = "AppBundleLocaleChangesDetector.splitDisabled"
    }

    private val localeChangeCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var localeSplitDisabled = false

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? = LOCALE_CHANGING_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val className = method.containingClass?.qualifiedName ?: return
        val isRelevant = when (method.name) {
            "setApplicationLocales" -> className == "androidx.appcompat.app.AppCompatDelegate"
            "setLocale", "setLocales" -> className == "android.content.res.Configuration"
            "updateConfiguration" -> className == "android.content.res.Resources"
            else -> false
        }
        if (isRelevant) {
            localeChangeCalls.add(context to node)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Reference checks are not required; method call interception covers all relevant APIs.
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && value.trim() == "false") {
            localeSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (localeChangeCalls.isNotEmpty() && !localeSplitDisabled) {
            for ((javaContext, node) in localeChangeCalls) {
                javaContext.report(
                    ISSUE,
                    node,
                    javaContext.getLocation(node),
                    "Runtime locale changes require disabling locale splits in the App Bundle configuration. " +
                    "Add `bundle { language { enableSplit = false } }` to your build.gradle, or use the " +
                    "Play Core library to download locales at runtime."
                )
            }
        }
        localeChangeCalls.clear()
        localeSplitDisabled = false
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        @Suppress("UNCHECKED_CAST")
        val partialCalls = partialResults.get(KEY_CALLS) as? List<Pair<JavaContext, UCallExpression>>
        if (partialCalls != null) {
            localeChangeCalls.addAll(partialCalls)
        }
        val partialDisabled = partialResults.get(KEY_SPLIT_DISABLED) as? Boolean
        if (partialDisabled == true) {
            localeSplitDisabled = true
        }
    }
}