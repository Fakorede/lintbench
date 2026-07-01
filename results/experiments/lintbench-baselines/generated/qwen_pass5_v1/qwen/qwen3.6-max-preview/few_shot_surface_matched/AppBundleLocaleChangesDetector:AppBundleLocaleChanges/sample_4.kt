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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var hasRuntimeLocaleChange = false
    private var hasPlayCoreUsage = false
    private var hasLocaleSplitDisabled = false
    private val localeChangeIncidents = mutableListOf<Incident>()

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setDefault",
        "setLocale",
        "setApplicationLocales",
        "startInstall"
    )

    override fun getApplicableReferenceNames(): List<String>? = listOf(
        "SplitInstallManager",
        "LocaleManager"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = method.name

        when (methodName) {
            "setDefault" -> {
                if (evaluator.isMemberInSubClassOf(method, "java.util.Locale")) {
                    recordLocaleChange(context, node)
                }
            }
            "setLocale" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration")) {
                    recordLocaleChange(context, node)
                }
            }
            "setApplicationLocales" -> {
                recordLocaleChange(context, node)
            }
            "startInstall" -> {
                if (evaluator.isMemberInSubClassOf(method, "com.google.android.play.core.splitinstall.SplitInstallManager")) {
                    hasPlayCoreUsage = true
                }
            }
        }
    }

    override fun visitReference(context: JavaContext, node: UReferenceExpression, resolved: PsiElement) {
        if (resolved is PsiClass) {
            val qualifiedName = resolved.qualifiedName
            if (qualifiedName == "com.google.android.play.core.splitinstall.SplitInstallManager" ||
                qualifiedName == "android.app.LocaleManager") {
                hasPlayCoreUsage = true
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        hasRuntimeLocaleChange = true
        val message = "Runtime locale change detected. Ensure `android.bundle.language.enableSplit` is set to `false` " +
            "or use the Play Core library to download additional locales at runtime."
        localeChangeIncidents.add(Incident(ISSUE, node, context.getLocation(node), message))
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
        if (property == "enableSplit" && value == "false") {
            if (parent == "language" || parentParent == "language") {
                hasLocaleSplitDisabled = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasRuntimeLocaleChange && !hasLocaleSplitDisabled && !hasPlayCoreUsage) {
            for (incident in localeChangeIncidents) {
                context.report(incident)
            }
        }
        hasRuntimeLocaleChange = false
        hasPlayCoreUsage = false
        hasLocaleSplitDisabled = false
        localeChangeIncidents.clear()
    }

    override fun checkPartialResults(context: Context) {
        // Partial results merging is handled by the lint infrastructure.
        // No custom merging logic required for this detector's state.
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle locale splitting configuration required for runtime locale changes",
            explanation = "When changing locales at runtime (e.g., to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale " +
                "(`android.bundle.language.enableSplit = false`) or the Play Core library must be used " +
                "to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.GRADLE_SCOPE
            ),
            androidSpecific = true
        )
    }
}