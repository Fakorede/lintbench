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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private class PendingReport(
        val location: Location
    )

    private fun getReportsKey(context: Context): String = 
        "AppBundleLocaleChangesDetector.REPORTS_" + context.project.name

    private fun getDisabledKey(context: Context): String = 
        "AppBundleLocaleChangesDetector.DISABLED_" + context.project.name

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApplicationLocales",
        "setLocale",
        "setLocales",
        "setDefault"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        val isLocaleChange = when (method.name) {
            "setApplicationLocales" -> {
                evaluator.isMemberInSubclassOf(containingClass, "androidx.appcompat.app.AppCompatDelegate", false) ||
                        evaluator.isMemberInSubclassOf(containingClass, "android.app.LocaleManager", false)
            }
            "setLocale", "setLocales" -> {
                evaluator.isMemberInSubclassOf(containingClass, "android.content.res.Configuration", false)
            }
            "setDefault" -> {
                evaluator.isMemberInSubclassOf(containingClass, "java.util.Locale", false)
            }
            else -> false
        }

        if (isLocaleChange) {
            val reportsKey = getReportsKey(context)
            @Suppress("UNCHECKED_CAST")
            val reports = context.client.getClientData(reportsKey) as? MutableList<PendingReport>
                ?: mutableListOf<PendingReport>().also { context.client.putClientData(reportsKey, it) }
            reports.add(PendingReport(context.getLocation(node)))
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
            context.client.putClientData(getDisabledKey(context), true)
        }
    }

    override fun afterCheckProject(context: Context) {
        val disabledKey = getDisabledKey(context)
        val reportsKey = getReportsKey(context)
        
        val disabled = context.client.getClientData(disabledKey) as? Boolean ?: false
        if (!disabled) {
            @Suppress("UNCHECKED_CAST")
            val reports = context.client.getClientData(reportsKey) as? List<PendingReport>
            if (reports != null) {
                for (report in reports) {
                    context.report(
                        ISSUE,
                        report.location,
                        "Found runtime locale change. Please ensure that the Android App Bundle is configured to not split by locale (`bundle { language { enableSplit = false } }`) or that the Play Core library is used to download additional locales."
                    )
                }
            }
        }
        context.client.putClientData(reportsKey, null)
        context.client.putClientData(disabledKey, null)
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