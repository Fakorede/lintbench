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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private val reports = mutableListOf<Location>()
    private var isLanguageSplitDisabled = false

    override fun beforeCheckProject(context: Context) {
        reports.clear()
        isLanguageSplitDisabled = false
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApplicationLocales",
        "setLocale",
        "setLocales",
        "setDefault",
        "updateConfiguration",
        "createConfigurationContext"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass
        val className = containingClass?.qualifiedName

        val isLocaleChange = when (method.name) {
            "setApplicationLocales" -> {
                className == null ||
                        className == "androidx.appcompat.app.AppCompatDelegate" ||
                        className == "android.app.LocaleManager" ||
                        evaluator.inheritsFrom(containingClass, "androidx.appcompat.app.AppCompatDelegate", false) ||
                        evaluator.inheritsFrom(containingClass, "android.app.LocaleManager", false)
            }
            "setLocale", "setLocales" -> {
                className == null ||
                        className == "android.content.res.Configuration" ||
                        evaluator.inheritsFrom(containingClass, "android.content.res.Configuration", false)
            }
            "setDefault" -> {
                className == null ||
                        className == "java.util.Locale" ||
                        className.contains("Locale") ||
                        evaluator.inheritsFrom(containingClass, "java.util.Locale", false)
            }
            "updateConfiguration" -> {
                className == null ||
                        className == "android.content.res.Resources" ||
                        evaluator.inheritsFrom(containingClass, "android.content.res.Resources", false)
            }
            "createConfigurationContext" -> {
                className == null ||
                        className == "android.content.Context" ||
                        evaluator.inheritsFrom(containingClass, "android.content.Context", false)
            }
            else -> false
        }

        if (isLocaleChange) {
            reports.add(context.getLocation(node))
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("locale", "locales")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val isConfigurationField = if (referenced is PsiField) {
            val containingClass = referenced.containingClass
            containingClass?.qualifiedName == "android.content.res.Configuration"
        } else {
            reference.resolvedName == "locale" || reference.resolvedName == "locales"
        }
        if (isConfigurationField) {
            reports.add(context.getLocation(reference))
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any
    ) {
        if (property == "enableSplit" && value == "false" && parent == "language" && parentParent == "bundle") {
            isLanguageSplitDisabled = true
        }
    }

    override fun afterCheckProject(context: Context) {
        if (!isLanguageSplitDisabled) {
            for (location in reports) {
                context.report(
                    ISSUE,
                    location,
                    "Found runtime locale change. Please ensure that the Android App Bundle is configured to not split by locale (`bundle { language { enableSplit = false } }`) or that the Play Core library is used to download additional locales."
                )
            }
        }
    }

    companion object {
        @JvmField
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
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }
}