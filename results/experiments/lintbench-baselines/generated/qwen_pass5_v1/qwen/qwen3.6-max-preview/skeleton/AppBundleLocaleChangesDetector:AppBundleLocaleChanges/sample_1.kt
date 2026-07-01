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
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core library " +
                "must be used to download additional locales at runtime. Otherwise, the app may crash or " +
                "fail to load resources for the new locale.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val LOCALE_CHANGE_METHODS = listOf(
            "setDefault", "setLocale", "updateConfiguration", "setApplicationLocales"
        )

        private val LOCALE_CHANGE_CLASSES = setOf(
            "java.util.Locale",
            "android.content.res.Configuration",
            "android.content.res.Resources",
            "androidx.appcompat.app.AppCompatDelegate"
        )
    }

    private var hasRuntimeLocaleChange = false
    private var runtimeLocaleChangeLocation: Location? = null
    private var hasDisabledLocaleSplit = false

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? = LOCALE_CHANGE_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (hasRuntimeLocaleChange) return

        val qualifiedName = context.evaluator.getMethodClass(method)?.qualifiedName
        if (qualifiedName in LOCALE_CHANGE_CLASSES) {
            hasRuntimeLocaleChange = true
            runtimeLocaleChangeLocation = context.getLocation(node)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Reference checking not required for this detector
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle" && value.trim() == "false") {
            hasDisabledLocaleSplit = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasRuntimeLocaleChange && !hasDisabledLocaleSplit) {
            val location = runtimeLocaleChangeLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "This app appears to change the locale at runtime. When using Android App Bundles, " +
                    "runtime locale changes require either disabling locale splits " +
                    "(`android.bundle.language.enableSplit = false`) or using the Play Core library " +
                    "to download additional locales dynamically."
            )
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Partial results not utilized by this detector
    }
}