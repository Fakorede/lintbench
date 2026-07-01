package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private const val KEY_LOCALE_CHANGE = "locale_change"
        private const val KEY_LOCALE_CHANGE_LOCATIONS = "locale_change_locations"
        private const val KEY_LANGUAGE_SPLIT = "language_split"

        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When your app allows users to change the language at runtime, resources for the
                newly selected locale must already be present on the device. If the app is
                distributed as an Android App Bundle with language splits enabled, the device
                only downloads the language matching the system locale by default.

                To support runtime locale changes, either disable language splits in the app
                bundle by setting `android.bundle.language.enableSplit = false`, or use the
                Play Core Library to download the required language splits at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setApplicationLocales",
        "setDefault",
        "setLocale",
        "setLocales",
        "updateConfiguration",
        "createConfigurationContext",
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!isLocaleChangeMethod(method)) {
            return
        }

        val partial = context.getPartialResult(ISSUE)
        partial.setBoolean(KEY_LOCALE_CHANGE, true)

        val location = context.getLocation(node)
        val line = location.start?.line ?: 0
        val newEntry = "${context.file.absolutePath}:$line"

        val existing = partial.getString(KEY_LOCALE_CHANGE_LOCATIONS)
        val updated = if (existing.isNullOrBlank()) {
            newEntry
        } else {
            "$existing\n$newEntry"
        }
        partial.setString(KEY_LOCALE_CHANGE_LOCATIONS, updated)
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Not needed; method calls are sufficient.
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            when (value) {
                "true", "false" -> context.getPartialResult(ISSUE)
                    .setString(KEY_LANGUAGE_SPLIT, value)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // All reporting is done in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val splitValue = partialResults.getString(KEY_LANGUAGE_SPLIT)
        val languageSplitEnabled = splitValue?.toBoolean() ?: true
        if (!languageSplitEnabled) {
            return
        }

        val locations = partialResults.getString(KEY_LOCALE_CHANGE_LOCATIONS) ?: return
        val message = "Runtime locale changes require the App Bundle to not split by language, " +
                "or use the Play Core Library to download additional language splits at runtime. " +
                "Disable language splitting with `android.bundle.language.enableSplit = false`."

        for (entry in locations.split("\n")) {
            if (entry.isBlank()) {
                continue
            }
            val separator = entry.lastIndexOf(':')
            if (separator <= 0) {
                continue
            }
            val path = entry.substring(0, separator)
            val line = entry.substring(separator + 1).toIntOrNull() ?: continue

            val location = Location.create(
                File(path),
                DefaultPosition(line, 0, -1),
                DefaultPosition(line, 0, -1),
            )
            context.report(ISSUE, location, message)
        }
    }

    private fun isLocaleChangeMethod(method: PsiMethod): Boolean {
        val className = method.containingClass?.qualifiedName ?: return false
        return when (method.name) {
            "setApplicationLocales" -> className == "androidx.appcompat.app.AppCompatDelegate" ||
                    className == "android.app.LocaleManager" ||
                    className == "androidx.core.app.LocaleManagerCompat"

            "setDefault" -> className == "java.util.Locale" ||
                    className == "android.os.LocaleList" ||
                    className == "androidx.core.os.LocaleListCompat"

            "setLocale", "setLocales" -> className == "android.content.res.Configuration"

            "updateConfiguration" -> className == "android.content.res.Resources"

            "createConfigurationContext" -> className == "android.content.Context"

            else -> false
        }
    }
}