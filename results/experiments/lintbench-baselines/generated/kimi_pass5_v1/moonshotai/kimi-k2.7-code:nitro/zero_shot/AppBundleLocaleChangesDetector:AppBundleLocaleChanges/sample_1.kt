package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUCallNames(): List<String> =
        listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "setApplicationLocales",
            "updateConfiguration",
            "createConfigurationContext"
        )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod?
    ) {
        if (method == null) return

        val evaluator = context.evaluator
        when (method.name) {
            "setLocale", "setLocales" -> if (evaluator.isMemberInSubClassOf(
                    method,
                    "android.content.res.Configuration",
                    false
                )
            ) report(context, node)

            "updateConfiguration" -> if (evaluator.isMemberInSubClassOf(
                    method,
                    "android.content.res.Resources",
                    false
                )
            ) report(context, node)

            "createConfigurationContext" -> if (evaluator.isMemberInSubClassOf(
                    method,
                    "android.content.Context",
                    false
                )
            ) report(context, node)

            "setDefault" -> if (evaluator.isMemberInSubClassOf(
                    method,
                    "java.util.Locale",
                    false
                )
            ) report(context, node)

            "setApplicationLocales" -> if (evaluator.isMemberInSubClassOf(
                    method,
                    "androidx.appcompat.app.AppCompatDelegate",
                    false
                )
            ) report(context, node)
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE
        )
    }

    companion object {
        private const val ID = "AppBundleLocaleChanges"
        private const val DESCRIPTION = "App Bundle handling of runtime locale changes"
        private const val EXPLANATION =
            "When changing locales at runtime, the Android App Bundle must either be " +
                    "configured to not split resources by locale (for example, by setting " +
                    "`bundle { language { enableSplit = false } }` in the build file), or " +
                    "the Play Core library must be used to download additional language splits " +
                    "at runtime."
        private const val MESSAGE =
            "Runtime locale changes may not work correctly with an Android App Bundle " +
                    "unless either `bundle` language splitting is disabled or the Play Core " +
                    "library is used to download missing language splits. " +
                    "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"

        @JvmField
        val ISSUE = Issue.create(
            id = ID,
            briefDescription = DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.I18N,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}