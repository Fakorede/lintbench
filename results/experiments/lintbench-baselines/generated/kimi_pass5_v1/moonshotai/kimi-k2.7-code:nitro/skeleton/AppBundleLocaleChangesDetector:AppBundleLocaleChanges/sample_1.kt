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
        private const val KEY_LOCALE_CHANGES = "locale_changes"
        private const val KEY_LANGUAGE_SPLIT = "language_split"
        private const val LOCATION_SEPARATOR = "|"

        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "Runtime locale changes may not work with App Bundle language splits",
            explanation = """
                When changing locales at runtime (for example, to provide an in-app
                language switcher), the Android App Bundle must be configured to not
                split by locale, or the Play Core library must be used to download
                additional locales at runtime.

                By default, App Bundles generate separate APKs for each language. If
                the user switches to a locale whose resources are not installed, the
                new locale's resources will not be available. To fix this, either set
                `bundle { language { enableSplit = false } }` in your module's
                `build.gradle` file, or use the Play Core library to download the
                required locale splits on demand.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun getApplicableMethodNames(): List<String>? =
        listOf("setApplicationLocales", "setDefault", "setLocale", "setLocales")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val name = method.name
        val evaluator = context.evaluator
        val isLocaleChange = when (name) {
            "setApplicationLocales" ->
                evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")
            "setDefault" ->
                evaluator.isMemberInClass(method, "java.util.Locale")
            "setLocale", "setLocales" ->
                evaluator.isMemberInClass(method, "android.content.res.Configuration")
            else -> false
        }
        if (isLocaleChange) {
            recordLocaleChange(context, node)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Actual runtime locale changes are method calls handled in visitMethodCall.
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            context.partialResults.put(
                KEY_LANGUAGE_SPLIT,
                if (value == "false") "false" else "true",
            )
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Partial results are combined and reported in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val languageSplit = partialResults.get(KEY_LANGUAGE_SPLIT) ?: "true"
        if (languageSplit == "false") {
            return
        }

        val locationsStr = partialResults.get(KEY_LOCALE_CHANGES) ?: return
        for (locationStr in locationsStr.split(LOCATION_SEPARATOR)) {
            val location = deserializeLocation(locationStr) ?: continue
            context.report(
                ISSUE,
                location,
                "Runtime locale changes require the App Bundle to disable language " +
                    "splits or use the Play Core library to fetch missing locales.",
            )
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        val location = context.getLocation(node)
        val serialized = serializeLocation(location)
        val partialResults = context.partialResults
        val existing = partialResults.get(KEY_LOCALE_CHANGES)
        val updated = if (existing.isNullOrEmpty()) {
            serialized
        } else {
            "$existing$LOCATION_SEPARATOR$serialized"
        }
        partialResults.put(KEY_LOCALE_CHANGES, updated)
    }

    private fun serializeLocation(location: Location): String {
        val file = location.file
        val start = location.startOffset
        val end = location.endOffset
        return "$file:$start:$end"
    }

    private fun deserializeLocation(serialized: String): Location? {
        val parts = serialized.split(":")
        if (parts.size < 3) {
            return null
        }
        val end = parts.last().toIntOrNull() ?: return null
        val start = parts[parts.size - 2].toIntOrNull() ?: return null
        val path = parts.subList(0, parts.size - 2).joinToString(":")
        return Location.create(File(path), null, start, end)
    }
}