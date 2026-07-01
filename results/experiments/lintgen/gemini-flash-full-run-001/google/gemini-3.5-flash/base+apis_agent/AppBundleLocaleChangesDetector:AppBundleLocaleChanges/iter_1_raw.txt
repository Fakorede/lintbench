package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node
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

    override fun visitBuildScript(context: GradleContext) {
        val text = context.getContents()?.toString() ?: ""
        val regex = Regex("""enableSplit\s*(=\s*|\.\s*set\s*\(\s*|\s+)false""")
        if (text.contains("bundle") && text.contains("language") && regex.containsMatchIn(text)) {
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
            "createConfigurationContext" -> evaluator.inheritsFrom(method.containingClass, "android.content.Context", false)
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
        usesPlayCoreSplitInstall = true
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