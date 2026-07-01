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
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("locale")

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setLocale",
        "setLocales",
        "setDefault",
        "createConfigurationContext"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        val methodName = method.name
        val matched = when (methodName) {
            "setLocale", "setLocales" -> {
                evaluator.isMemberInClass(method, "android.content.res.Configuration") ||
                evaluator.isMemberInClass(method, "androidx.core.os.ConfigurationCompat")
            }
            "setDefault" -> {
                evaluator.isMemberInClass(method, "java.util.Locale")
            }
            "createConfigurationContext" -> {
                evaluator.isMemberInClass(method, "android.content.Context") ||
                evaluator.isMemberInSubclassOf(method, "android.content.Context")
            }
            else -> false
        }

        if (matched) {
            reportLocaleChange(context, context.getLocation(node))
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val evaluator = context.evaluator
        if (referenced is PsiMethod) {
            val name = referenced.name
            if ((name == "setLocale" || name == "getLocale") && evaluator.isMemberInClass(referenced, "android.content.res.Configuration")) {
                reportLocaleChange(context, context.getLocation(reference))
            }
        } else if (referenced is PsiField) {
            val name = referenced.name
            if (name == "locale" && evaluator.isMemberInClass(referenced, "android.content.res.Configuration")) {
                reportLocaleChange(context, context.getLocation(reference))
            }
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && (value == "false" || value == "Boolean.FALSE")) {
            if (parent == "language" && parentParent == "bundle") {
                val project = context.project
                val partialResults = context.getPartialResults(ISSUE)
                val map = partialResults.map(project)
                map.put("splitsDisabled", true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op: all analysis is completed and reported in checkPartialResults
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val projects = partialResults.projects()
        val splitsDisabledProjects = mutableSetOf<String>()

        for (project in projects) {
            val map = partialResults.map(project)
            if (map.getBoolean("splitsDisabled") == true) {
                splitsDisabledProjects.add(project.name)
            }
        }

        for (project in projects) {
            val map = partialResults.map(project)
            val localeChangesStr = map.getString("localeChanges") ?: continue
            
            val isSafe = isSplitsDisabledForProject(project, splitsDisabledProjects)
            if (!isSafe) {
                val serializedLocations = localeChangesStr.split(";")
                for (serialized in serializedLocations) {
                    if (serialized.isEmpty()) continue
                    val location = deserializeLocation(serialized)
                    context.report(
                        ISSUE,
                        location,
                        "When changing locales at runtime, the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime."
                    )
                }
            }
        }
    }

    private fun reportLocaleChange(context: JavaContext, location: Location) {
        val project = context.project
        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map(project)
        val existing = map.getString("localeChanges")
        val serialized = serializeLocation(location)
        val updated = if (existing.isNullOrEmpty()) serialized else "$existing;$serialized"
        map.put("localeChanges", updated)
    }

    private fun serializeLocation(location: Location): String {
        val file = location.file.absolutePath
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        return "$file:$start:$end"
    }

    private fun deserializeLocation(serialized: String): Location {
        val lastColon = serialized.lastIndexOf(':')
        val secondLastColon = serialized.lastIndexOf(':', lastColon - 1)
        val file = java.io.File(serialized.substring(0, secondLastColon))
        val startOffset = serialized.substring(secondLastColon + 1, lastColon).toInt()
        val endOffset = serialized.substring(lastColon + 1).toInt()
        val start = DefaultPosition(-1, -1, startOffset)
        val end = DefaultPosition(-1, -1, endOffset)
        return Location.create(file, start, end)
    }

    private fun isSplitsDisabledForProject(
        project: com.android.tools.lint.detector.api.Project,
        splitsDisabledProjects: Set<String>
    ): Boolean {
        if (project.isAndroidProject && !project.isLibrary) {
            return splitsDisabledProjects.contains(project.name)
        }
        return splitsDisabledProjects.isNotEmpty()
    }
}