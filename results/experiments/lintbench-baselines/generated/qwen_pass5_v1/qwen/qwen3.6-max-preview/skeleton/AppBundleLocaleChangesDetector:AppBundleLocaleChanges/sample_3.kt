package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private val localeChanges = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var localeSplitDisabled = false
    private var playCoreUsed = false

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale " +
                "(`android.bundle.language.enableSplit = false`) or the Play Core library must be used " +
                "to download additional locales at runtime. Otherwise, the app may crash or fail to load " +
                "resources for the new locale on devices that didn't install that language split.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val LOCALE_CLASS = "java.util.Locale"
    }

    override fun beforeCheckEachProject(context: Context) {
        localeChanges.clear()
        localeSplitDisabled = false
        playCoreUsed = false
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? = listOf("setLocale", "updateConfiguration", "setDefault")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        val isLocaleChange = when (methodName) {
            "setLocale" -> containingClass == CONFIGURATION_CLASS
            "updateConfiguration" -> containingClass == RESOURCES_CLASS
            "setDefault" -> containingClass == LOCALE_CLASS
            else -> false
        }

        if (isLocaleChange) {
            localeChanges.add(context to node)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val qualifiedName = when (referenced) {
            is PsiMethod -> referenced.containingClass?.qualifiedName
            is PsiClass -> referenced.qualifiedName
            else -> null
        }
        if (qualifiedName != null && qualifiedName.startsWith("com.google.android.play.core.splitinstall")) {
            playCoreUsed = true
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && value == "false") {
            localeSplitDisabled = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (localeSplitDisabled || playCoreUsed) {
            return
        }
        for ((ctx, node) in localeChanges) {
            ctx.report(
                ISSUE,
                node,
                ctx.getLocation(node),
                "Runtime locale changes require disabling App Bundle locale splits " +
                    "(`android.bundle.language.enableSplit = false`) or using the Play Core library " +
                    "to download language splits at runtime."
            )
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op: state is aggregated in memory per project run.
        // Incremental analysis merges are handled by the Lint framework automatically
        // for simple in-memory aggregation detectors.
    }
}