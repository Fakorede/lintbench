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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var runtimeLocaleChangeDetected = false
    private var localeSplitDisabled = false
    private var localeChangeContext: JavaContext? = null
    private var localeChangeNode: UElement? = null

    override fun getApplicableMethodNames(): List<String>? = listOf("setApplicationLocales", "setLocale")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        if ((qualifiedName == "androidx.appcompat.app.AppCompatDelegate" && method.name == "setApplicationLocales") ||
            (qualifiedName == "android.content.res.Configuration" && method.name == "setLocale")) {
            runtimeLocaleChangeDetected = true
            localeChangeContext = context
            localeChangeNode = node
        }
    }

    override fun getApplicableReferenceNames(): List<String>? = listOf("setApplicationLocales", "setLocale")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, resolved: PsiElement) {
        if (resolved is PsiMethod) {
            val qualifiedName = resolved.containingClass?.qualifiedName ?: return
            if ((qualifiedName == "androidx.appcompat.app.AppCompatDelegate" && resolved.name == "setApplicationLocales") ||
                (qualifiedName == "android.content.res.Configuration" && resolved.name == "setLocale")) {
                runtimeLocaleChangeDetected = true
                localeChangeContext = context
                localeChangeNode = reference
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
        if (property == "enableSplit" && parent == "language" && value == "false") {
            localeSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (runtimeLocaleChangeDetected && !localeSplitDisabled && localeChangeContext != null && localeChangeNode != null) {
            val loc = localeChangeContext!!.getLocation(localeChangeNode!!)
            val incident = Incident(
                ISSUE,
                localeChangeNode!!,
                loc,
                "Runtime locale change detected without disabling App Bundle language splits. " +
                    "Set `android.bundle.language.enableSplit = false` in your build.gradle or use Play Core to download locales."
            )
            context.client.report(context, incident)
        }
        runtimeLocaleChangeDetected = false
        localeSplitDisabled = false
        localeChangeContext = null
        localeChangeNode = null
    }

    override fun checkPartialResults(context: Context) {
        afterCheckEachProject(context)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle must be configured to handle runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core " +
                "library must be used to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true,
        )
    }
}