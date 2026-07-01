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
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApplicationLocales",
        "getApplicationLocales",
        "setLocales",
        "setDefaultLocales"
    )

    override fun getApplicableReferenceNames(): List<String> = listOf(
        "LocaleListCompat",
        "LocaleChanger",
        "SplitInstallManager",
        "SplitInstallManagerFactory",
        "SplitInstallRequest",
        "SplitInstallStateUpdatedListener",
        "SplitInstallException"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isLocaleChangeMethod(context, method)) {
            context.getPartialResults(ISSUE).map().putBoolean(HAS_LOCALE_CHANGE, true)
        }
    }

    override fun visitReference(context: JavaContext, node: UReferenceExpression) {
        when (node.asSourceString()) {
            in LOCALE_REFERENCE_NAMES -> {
                context.getPartialResults(ISSUE).map().putBoolean(HAS_LOCALE_CHANGE, true)
            }
            in PLAY_CORE_REFERENCE_NAMES -> {
                context.getPartialResults(ISSUE).map().putBoolean(USES_PLAY_CORE, true)
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
        if (property == "enableSplit" && (parent == "language" || parentParent == "language")) {
            val enabled = value.toBooleanStrictOrNull() ?: true
            context.getPartialResults(ISSUE).map().putBoolean(ENABLE_SPLIT, enabled)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Cross-module aggregation and final reporting are performed in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResult: PartialResult) {
        val map = partialResult.map()
        val hasLocaleChange = map.getBoolean(HAS_LOCALE_CHANGE, false)
        val enableSplit = map.getBoolean(ENABLE_SPLIT, true)
        val usesPlayCore = map.getBoolean(USES_PLAY_CORE, false)

        if (hasLocaleChange && enableSplit && !usesPlayCore) {
            context.report(
                Incident(
                    ISSUE,
                    context.getRangeLocation(0, 0),
                    MESSAGE
                )
            )
        }
    }

    private fun isLocaleChangeMethod(context: JavaContext, method: PsiMethod): Boolean {
        return when (method.name) {
            "setApplicationLocales", "getApplicationLocales" -> {
                context.evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")
            }
            "setLocales", "setDefaultLocales" -> {
                context.evaluator.isMemberInClass(method, "android.content.res.Configuration") ||
                    context.evaluator.isMemberInClass(method, "android.os.LocaleList")
            }
            else -> false
        }
    }

    companion object {
        private const val HAS_LOCALE_CHANGE = "hasLocaleChange"
        private const val ENABLE_SPLIT = "enableSplit"
        private const val USES_PLAY_CORE = "usesPlayCore"

        private const val MESSAGE =
            "App Bundle is configured to split by language, but the app handles runtime locale changes. " +
                "Either set `android.bundle.language.enableSplit` to `false` or use the Play Core library " +
                "to download additional language splits at runtime."

        private val LOCALE_REFERENCE_NAMES = listOf(
            "LocaleListCompat",
            "LocaleChanger"
        )

        private val PLAY_CORE_REFERENCE_NAMES = listOf(
            "SplitInstallManager",
            "SplitInstallManagerFactory",
            "SplitInstallRequest",
            "SplitInstallStateUpdatedListener",
            "SplitInstallException"
        )

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle locale split conflicts with runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher),
                the Android App Bundle must be configured to not split by locale, or the Play
                Core library must be used to download additional language splits at runtime.

                To disable language splits, add the following to your build.gradle file:

                    android.bundle.language.enableSplit = false

                Alternatively, use the Play Core Split Install API to fetch missing language splits.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true,
        )
    }
}