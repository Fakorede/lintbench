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
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private val localeChangeLocations = mutableListOf<Location>()
    private var isLanguageSplitDisabled = false

    override fun getApplicableReferenceNames(): List<String>? = listOf("Locale", "AppCompatDelegate", "Configuration")

    override fun getApplicableMethodNames(): List<String>? = listOf("setApplicationLocales", "setLocale", "setDefault")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val isInTargetClass = evaluator.isMemberInSubClassOf(method, "androidx.appcompat.app.AppCompatDelegate", false) ||
                evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration", false) ||
                evaluator.isMemberInSubClassOf(method, "java.util.Locale", false)

        if (isInTargetClass) {
            localeChangeLocations.add(context.getLocation(node))
        }
    }

    override fun visitReference(context: JavaContext, node: UReferenceExpression, resolved: PsiElement) {
        // Reference tracking is handled via method calls for this detector.
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
        if (property == "enableSplit" && parent == "language" && value == "false") {
            isLanguageSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!isLanguageSplitDisabled && localeChangeLocations.isNotEmpty()) {
            for (location in localeChangeLocations) {
                val incident = Incident(
                    ISSUE,
                    location,
                    location,
                    "Runtime locale changes require disabling App Bundle language splits or using Play Core to download locales."
                )
                context.client.report(context, incident)
            }
        }
        localeChangeLocations.clear()
        isLanguageSplitDisabled = false
    }

    override fun checkPartialResults(context: Context, results: List<PartialResult>) {
        // Merge partial results for incremental analysis if necessary.
        // Standard pass-through for this detector's scope.
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale (`android.bundle.language.enableSplit = false`) " +
                "or the Play Core library must be used to download additional locales at runtime. " +
                "Otherwise, resources for the new locale may not be available on the device.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                setOf(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true
        )
    }
}