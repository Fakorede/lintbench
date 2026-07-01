package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    private val classPipUsage = mutableMapOf<String, PipUsage>()

    private data class PipUsage(
        val className: String,
        var hasEnterPip: Boolean = false,
        var hasAutoEnter: Boolean = false,
        var hasSourceRect: Boolean = false,
        var enterPipLocation: Location? = null
    )

    override fun getApplicableMethodNames(): List<String> {
        return listOf("enterPictureInPictureMode", "setAutoEnterEnabled", "setSourceRectHint")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = node.getContainingUClass()?.qualifiedName ?: return
        val usage = classPipUsage.getOrPut(containingClass) { PipUsage(containingClass) }

        val methodName = method.name
        if (methodName == "enterPictureInPictureMode") {
            if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
                usage.hasEnterPip = true
                if (usage.enterPipLocation == null) {
                    usage.enterPipLocation = context.getLocation(node)
                }
            }
        } else if (methodName == "setAutoEnterEnabled") {
            if (context.evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
                usage.hasAutoEnter = true
            }
        } else if (methodName == "setSourceRectHint") {
            if (context.evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
                usage.hasSourceRect = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE)
        val projectMap = partialResults.map(context.project)

        for ((className, usage) in classPipUsage) {
            if (usage.hasEnterPip) {
                val location = usage.enterPipLocation ?: continue
                val keyPrefix = "pip_$className"
                projectMap.put("$keyPrefix:hasAutoEnter", usage.hasAutoEnter)
                projectMap.put("$keyPrefix:hasSourceRect", usage.hasSourceRect)
                projectMap.put("$keyPrefix:file", location.file.absolutePath)
                location.start?.let { projectMap.put("$keyPrefix:start", it.offset) }
                location.end?.let { projectMap.put("$keyPrefix:end", it.offset) }
            }
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (project in partialResults.projects()) {
            val projectMap = partialResults.map(project)
            val keys = projectMap.keys()
            val classes = keys.filter { it.startsWith("pip_") }.map {
                it.substringAfter("pip_").substringBefore(":")
            }.distinct()

            for (className in classes) {
                val keyPrefix = "pip_$className"
                val hasAutoEnter = projectMap.getBoolean("$keyPrefix:hasAutoEnter", false)
                val hasSourceRect = projectMap.getBoolean("$keyPrefix:hasSourceRect", false)

                if (!hasAutoEnter || !hasSourceRect) {
                    val filePath = projectMap.getString("$keyPrefix:file") ?: continue
                    val file = java.io.File(filePath)
                    val start = projectMap.getInt("$keyPrefix:start", -1)
                    val end = projectMap.getInt("$keyPrefix:end", -1)

                    val location = if (start >= 0 && end >= 0) {
                        Location.create(file, start, end)
                    } else {
                        Location.create(file)
                    }

                    val message = "To support smoother transitions into picture-in-picture, " +
                            "you should call both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
                            "on your `PictureInPictureParams.Builder`."

                    context.report(Incident(ISSUE, location, message))
                }
            }
        }
    }
}