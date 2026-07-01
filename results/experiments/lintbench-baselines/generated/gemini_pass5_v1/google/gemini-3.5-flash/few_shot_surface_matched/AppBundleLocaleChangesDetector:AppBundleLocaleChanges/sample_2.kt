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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var hasLocaleChanges = false
    private var hasPlayCoreLanguage = false
    private var languageSplitDisabled = false
    private val localeChangeLocations = mutableListOf<Location>()

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("locale", "locales")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setLocale", "setLocales", "setApplicationLocales", "addLanguage")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        if (name == "addLanguage") {
            hasPlayCoreLanguage = true
        } else if (name == "setLocale" || name == "setLocales" || name == "setApplicationLocales") {
            hasLocaleChanges = true
            localeChangeLocations.add(context.getLocation(node))
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val name = reference.resolvedName
        if (name == "locale" || name == "locales") {
            hasLocaleChanges = true
            localeChangeLocations.add(context.getLocation(reference))
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
        if (property == "enableSplit" && parent == "language" && value == "false") {
            languageSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasLocaleChanges && !hasPlayCoreLanguage && !languageSplitDisabled) {
            for (location in localeChangeLocations) {
                val incident = Incident(
                    ISSUE,
                    location,
                    "When changing locales at runtime, the Android App Bundle must be configured " +
                        "to not split by locale or the Play Core library must be used to download " +
                        "additional locales at runtime."
                )
                context.report(incident)
            }
        }
        // Reset state for the next project
        hasLocaleChanges = false
        hasPlayCoreLanguage = false
        languageSplitDisabled = false
        localeChangeLocations.clear()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        // No-op: check is handled per-project in afterCheckEachProject
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
                java.util.EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            ),
            androidSpecific = true
        )
    }
}