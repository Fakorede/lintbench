package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApplicationLocales",
        "setLocale",
        "setLocales",
        "setDefault"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val isLocaleChange = when (method.name) {
            "setApplicationLocales" -> {
                evaluator.isMemberInSubclassOf(method, "androidx.appcompat.app.AppCompatDelegate", false) ||
                        evaluator.isMemberInSubclassOf(method, "android.app.LocaleManager", false)
            }
            "setLocale", "setLocales" -> {
                evaluator.isMemberInSubclassOf(method, "android.content.res.Configuration", false)
            }
            "setDefault" -> {
                evaluator.isMemberInSubclassOf(method, "java.util.Locale", false)
            }
            else -> false
        }

        if (isLocaleChange) {
            val disabled = context.mainProject.getProperty(KEY) as? Boolean ?: false
            if (!disabled) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Found runtime locale change. Please ensure that the Android App Bundle is configured to not split by locale (`bundle { language { enableSplit = false } }`) or that the Play Core library is used to download additional locales."
                )
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
        valueCookie: Any
    ) {
        if (property == "enableSplit" && value == "false" && parent == "language" && parentParent == "bundle") {
            context.project.putProperty(KEY, true)
        }
    }

    companion object {
        private const val KEY = "com.android.tools.lint.checks.AppBundleLocaleChangesDetector.DISABLED"

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