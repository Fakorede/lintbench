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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private val pendingReports = mutableListOf<PendingReport>()
    private var languageSplitDisabled = false
    private var usesPlayCoreSplitInstall = false

    private class PendingReport(
        val context: JavaContext,
        val location: Location
    )

    override fun beforeCheckEachProject(context: Context) {
        pendingReports.clear()
        languageSplitDisabled = false
        usesPlayCoreSplitInstall = false
    }

    override fun visitBuildGradle(context: GradleContext) {
        val text = context.sourceText
        val hasBundle = text.contains("bundle")
        val hasLanguage = text.contains("language")
        val hasEnableSplitFalse = text.contains("enableSplit") && text.contains("false")
        if (hasBundle && hasLanguage && hasEnableSplitFalse) {
            languageSplitDisabled = true
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "setLocale",
            "setLocales",
            "createConfigurationContext",
            "setApplicationLocales"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val isTarget = when (method.name) {
            "setDefault" -> evaluator.isMemberInClass(method, "java.util.Locale") || evaluator.isMemberInClass(method, "android.os.LocaleList")
            "setLocale", "setLocales" -> evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "createConfigurationContext" -> evaluator.isMemberInSubclassOf(method, "android.content.Context")
            "setApplicationLocales" -> evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")
            else -> false
        }

        if (isTarget) {
            pendingReports.add(PendingReport(context, context.getLocation(node)))
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("SplitInstallManager", "SplitInstallRequest", "SplitInstallManagerFactory")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val name = reference.asSourceString()
        if (name.contains("SplitInstallManager") || name.contains("SplitInstallRequest")) {
            usesPlayCoreSplitInstall = true
            return
        }
        if (referenced is PsiClass) {
            val qualifiedName = referenced.qualifiedName
            if (qualifiedName != null && qualifiedName.contains("play.core.splitinstall")) {
                usesPlayCoreSplitInstall = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!languageSplitDisabled && !usesPlayCoreSplitInstall) {
            for (report in pendingReports) {
                report.context.report(
                    ISSUE,
                    report.location,
                    "Runtime locale changes require disabling language splits in bundle configuration or using Play Core SplitInstallManager"
                )
            }
        }
        pendingReports.clear()
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
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