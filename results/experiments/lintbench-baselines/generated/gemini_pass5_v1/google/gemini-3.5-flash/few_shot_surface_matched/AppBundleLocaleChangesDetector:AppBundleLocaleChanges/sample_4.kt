package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var languageSplitsDisabled = false
    private val localeChangeLocations = mutableListOf<com.android.tools.lint.detector.api.Location>()

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("locale")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setDefault", "setLocale", "setLocales", "setApplicationLocales")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val name = method.name

        val isLocaleChange = when (name) {
            "setDefault" -> evaluator.isMemberInClass(method, "java.util.Locale")
            "setLocale", "setLocales" -> evaluator.isMemberInClass(method, "android.content.res.Configuration") || 
                    evaluator.isMemberInClass(method, "android.os.LocaleList")
            "setApplicationLocales" -> evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate") || 
                    evaluator.isMemberInClass(method, "android.app.LocaleManager")
            else -> false
        }

        if (isLocaleChange) {
            localeChangeLocations.add(context.getLocation(node))
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField && referenced.name == "locale") {
            if (context.evaluator.isMemberInClass(referenced, "android.content.res.Configuration")) {
                localeChangeLocations.add(context.getLocation(reference))
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
        statementCookie: Any
    ) {
        if (property == "enableSplit" && value == "false" && parent == "language") {
            languageSplitsDisabled = true
        }
    }

    override fun afterCheckEachProject(context: com.android.tools.lint.detector.api.Context) {
        val project = context.project
        if (project.isLibrary) return

        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map(project)

        val locationStrings = localeChangeLocations.map { location ->
            val file = location.file.absolutePath
            val start = location.start?.offset ?: -1
            val end = location.end?.offset ?: -1
            "$file:$start:$end"
        }

        map.put("locations", locationStrings)
        map.put("languageSplitsDisabled", languageSplitsDisabled)
        map.put("hasLocaleChanges", localeChangeLocations.isNotEmpty())

        languageSplitsDisabled = false
        localeChangeLocations.clear()
    }

    override fun checkPartialResults(
        context: com.android.tools.lint.detector.api.Context,
        partialResults: com.android.tools.lint.detector.api.PartialResults
    ) {
        val project = context.project
        if (project.isLibrary) return

        val map = partialResults.map(project)
        val splitsDisabled = map.getBoolean("languageSplitsDisabled") ?: false
        if (splitsDisabled) return

        val hasChanges = map.getBoolean("hasLocaleChanges") ?: false
        if (!hasChanges) return

        val locationsSeq = map.getStringList("locations") ?: return
        for (locStr in locationsSeq) {
            val parts = locStr.split(":")
            if (parts.size >= 3) {
                val file = java.io.File(parts[0])
                val start = parts[1].toIntOrNull() ?: -1
                val end = parts[2].toIntOrNull() ?: -1
                val location = if (start >= 0 && end >= 0) {
                    com.android.tools.lint.detector.api.Location.create(file, context.client.getSourceText(file), start, end)
                } else {
                    com.android.tools.lint.detector.api.Location.create(file)
                }
                val incident = Incident(
                    ISSUE,
                    location,
                    "When changing locales at runtime, the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime."
                )
                context.report(incident)
            }
        }
    }

    companion object {
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
                Scope.JAVA_AND_GRADLE_FILES
            ),
            androidSpecific = true,
        )
    }
}