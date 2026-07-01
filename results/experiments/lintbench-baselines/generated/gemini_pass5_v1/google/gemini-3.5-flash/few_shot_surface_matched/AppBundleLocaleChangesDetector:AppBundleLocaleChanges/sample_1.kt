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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var languageSplitDisabled = false
    private val localeChanges = mutableListOf<Incident>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setDefault",
        "setLocale",
        "setLocales",
        "setApplicationLocales"
    )

    override fun getApplicableReferenceNames(): List<String> = listOf(
        "locale"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val isLocaleChange = when (method.name) {
            "setDefault" -> evaluator.isMemberInSubClassOf(method, "java.util.Locale") ||
                    evaluator.isMemberInSubClassOf(method, "android.os.LocaleList")
            "setLocale", "setLocales" -> evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration")
            "setApplicationLocales" -> evaluator.isMemberInSubClassOf(method, "androidx.appcompat.app.AppCompatDelegate")
            else -> false
        }
        if (isLocaleChange) {
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Runtime locale changes require disabling App Bundle language splitting or using Play Core"
            )
            localeChanges.add(incident)
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField && referenced.name == "locale") {
            val containingClass = referenced.containingClass
            if (containingClass?.qualifiedName == "android.content.res.Configuration") {
                val incident = Incident(
                    ISSUE,
                    reference,
                    context.getLocation(reference),
                    "Runtime locale changes require disabling App Bundle language splitting or using Play Core"
                )
                localeChanges.add(incident)
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
        if (property == "enableSplit" && value == "false" && parent == "language" && parentParent == "bundle") {
            languageSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!languageSplitDisabled) {
            for (incident in localeChanges) {
                context.report(incident)
            }
        }
        localeChanges.clear()
        languageSplitDisabled = false
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        if (!languageSplitDisabled) {
            for (incident in localeChanges) {
                context.report(incident)
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
                Scope.JAVA_AND_GRADLE_FILES
            )
        )
    }
}