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

    private var languageSplitDisabled = false
    private var localeChanged = false
    private var localeChangeLocation: Location? = null

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "setApplicationLocales",
            "updateConfiguration",
            "setLocale",
            "setLocales",
            "createConfigurationContext"
        )
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("Locale", "AppCompatDelegate")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val evaluator = context.evaluator

        val isLocaleChange = when (name) {
            "setDefault" -> evaluator.isMemberInSubClassOf(method, "java.util.Locale")
            "setApplicationLocales" -> evaluator.isMemberInSubClassOf(method, "androidx.appcompat.app.AppCompatDelegate")
            "updateConfiguration" -> evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")
            "setLocale", "setLocales" -> evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration")
            "createConfigurationContext" -> evaluator.isMemberInSubClassOf(method, "android.content.Context")
            else -> false
        }

        if (isLocaleChange) {
            localeChanged = true
            if (localeChangeLocation == null) {
                localeChangeLocation = context.getLocation(node)
            }
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        // Required override. Reference tracking can be used for extra flow analysis if needed.
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
            languageSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (localeChanged && !languageSplitDisabled) {
            val incident = Incident(
                ISSUE,
                localeChangeLocation ?: Location.create(context.file),
                "To support dynamic locale changes, you must configure the App Bundle to not split by locale or use the Play Core library."
            )
            context.report(incident)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Required override for global/partial analysis.
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