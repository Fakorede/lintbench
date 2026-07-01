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
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setDefault",
        "setLocale",
        "setLocales",
        "applyOverrideConfiguration",
        "createConfigurationContext",
        "updateConfiguration",
        "setApplicationLocales",
        "startInstall",
        "addLanguage"
    )

    override fun getApplicableReferenceNames(): List<String> = listOf(
        "SplitInstallManager",
        "SplitInstallRequest",
        "SplitInstallManagerFactory"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "setDefault" -> {
                val className = method.containingClass?.qualifiedName
                if (className == "java.util.Locale" || className == "android.icu.util.ULocale") {
                    recordLocaleChange(context, node)
                }
            }
            "setLocale", "setLocales" -> {
                if (context.evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
                    recordLocaleChange(context, node)
                }
            }
            "applyOverrideConfiguration" -> {
                if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
                    recordLocaleChange(context, node)
                }
            }
            "createConfigurationContext" -> {
                if (context.evaluator.isMemberInSubClassOf(method, "android.content.Context")) {
                    recordLocaleChange(context, node)
                }
            }
            "updateConfiguration" -> {
                if (context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
                    recordLocaleChange(context, node)
                }
            }
            "setApplicationLocales" -> {
                if (context.evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")) {
                    recordLocaleChange(context, node)
                }
            }
            "startInstall" -> {
                if (context.evaluator.isMemberInClass(
                        method,
                        "com.google.android.play.core.splitinstall.SplitInstallManager"
                    )
                ) {
                    recordPlayCoreUsage(context)
                }
            }
            "addLanguage" -> {
                val className = method.containingClass?.qualifiedName
                if (className == "com.google.android.play.core.splitinstall.SplitInstallRequest.Builder") {
                    recordPlayCoreUsage(context)
                }
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (isPlayCoreReference(referenced)) {
            recordPlayCoreUsage(context)
        }
    }

    private fun isPlayCoreReference(element: PsiElement): Boolean {
        val qualifiedName = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod -> element.containingClass?.qualifiedName
            is PsiField -> element.containingClass?.qualifiedName
            is PsiVariable -> element.type.canonicalText
            else -> null
        }
        return qualifiedName?.startsWith("com.google.android.play.core.splitinstall") == true
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
        if (property != "enableSplit" || parent != "language") return
        if (parentParent != "bundle" && parentParent != "android.bundle") return
        if (value.trim() == "false") {
            recordLanguageSplitDisabled(context)
        }
    }

    override fun checkPartialResults(context: Context, partialResult: PartialResult) {
        val map = partialResult.map
        val shouldReport = map.getBoolean(KEY_LOCALE_CHANGE, false) &&
            !map.getBoolean(KEY_LANGUAGE_SPLIT_DISABLED, false) &&
            !map.getBoolean(KEY_PLAY_CORE_DOWNLOAD, false)
        map.putBoolean(KEY_SHOULD_REPORT, shouldReport)
    }

    override fun afterCheckEachProject(context: Context) {
        val map = context.getPartialResults(ISSUE).map
        if (!map.getBoolean(KEY_SHOULD_REPORT, false)) return

        val filePath = map.getString(KEY_LOCATION_FILE, null) ?: return
        val offset = map.getInt(KEY_LOCATION_OFFSET, -1)
        if (offset == -1) return

        val file = File(filePath)
        if (!file.exists()) return

        val location = context.getRangeLocation(file, offset, offset)
        val message =
            "App bundles are split by language by default. If your app changes locales at runtime, " +
                "disable language splits with `bundle { language { enableSplit = false } }` or use the " +
                "Play Core library to download additional locales at runtime."
        val incident = Incident(ISSUE, file, location, message)
        context.client.report(context, incident)
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        val partialResult = context.getPartialResults(ISSUE)
        val map = partialResult.map
        if (!map.getBoolean(KEY_LOCALE_CHANGE, false)) {
            map.putBoolean(KEY_LOCALE_CHANGE, true)
            val offset = node.sourcePsi?.textRange?.startOffset ?: -1
            map.putString(KEY_LOCATION_FILE, context.file.path)
            map.putInt(KEY_LOCATION_OFFSET, offset)
        }
    }

    private fun recordPlayCoreUsage(context: JavaContext) {
        context.getPartialResults(ISSUE).map.putBoolean(KEY_PLAY_CORE_DOWNLOAD, true)
    }

    private fun recordLanguageSplitDisabled(context: GradleContext) {
        context.getPartialResults(ISSUE).map.putBoolean(KEY_LANGUAGE_SPLIT_DISABLED, true)
    }

    companion object {
        private const val KEY_LOCALE_CHANGE = "locale_change"
        private const val KEY_LANGUAGE_SPLIT_DISABLED = "language_split_disabled"
        private const val KEY_PLAY_CORE_DOWNLOAD = "play_core_download"
        private const val KEY_SHOULD_REPORT = "should_report"
        private const val KEY_LOCATION_FILE = "location_file"
        private const val KEY_LOCATION_OFFSET = "location_offset"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle is not configured for runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher),
                the Android App Bundle must be configured to not split by locale or the Play Core
                library must be used to download additional locales at runtime. Otherwise, users may
                not see the new language because the resources for that language may not be installed.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE)
            ),
            androidSpecific = true,
        )
    }
}