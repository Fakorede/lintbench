package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.ProjectContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.io.File
import org.jetbrains.uast.UCallExpression

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
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setAutoEnterEnabled",
            "setSourceRectHint",
            "enterPictureInPictureMode",
            "setPictureInPictureParams"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val map = context.getPartialResults(ISSUE).map(context.project)

        when (methodName) {
            "setAutoEnterEnabled" -> {
                val arg = node.valueArguments.firstOrNull()
                val value = arg?.evaluate()
                if (value == true) {
                    map.put("hasAutoEnterEnabled", true)
                }
            }
            "setSourceRectHint" -> {
                map.put("hasSourceRectHint", true)
            }
            "enterPictureInPictureMode", "setPictureInPictureParams" -> {
                val location = context.getLocation(node)
                val serialized = serializeLocation(location)
                val existing = map.getString("pipCalls")
                val updated = if (existing.isNullOrEmpty()) serialized else "$existing|$serialized"
                map.put("pipCalls", updated)
            }
        }
    }

    override fun afterCheckEachProject(context: ProjectContext) {
        // Method overridden as specified. Analysis is finalized in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        var hasAutoEnter = false
        var hasSourceRect = false
        val allPipCalls = mutableListOf<String>()

        for (target in partialResults.targets(ISSUE)) {
            val map = target.map
            if (map.getBoolean("hasAutoEnterEnabled") == true) {
                hasAutoEnter = true
            }
            if (map.getBoolean("hasSourceRectHint") == true) {
                hasSourceRect = true
            }
            val serializedCalls = map.getString("pipCalls")
            if (!serializedCalls.isNullOrEmpty()) {
                allPipCalls.addAll(serializedCalls.split("|"))
            }
        }

        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk in 1..30) {
            return
        }

        if (!hasAutoEnter || !hasSourceRect) {
            for (serializedCall in allPipCalls) {
                val location = deserializeLocation(serializedCall) ?: continue
                val message = "To support smoother hands-free PiP transitions, please call setAutoEnterEnabled(true) and setSourceRectHint(...) on PictureInPictureParams.Builder."
                val incident = Incident(ISSUE, message, location)
                context.report(incident)
            }
        }
    }

    private fun serializeLocation(location: Location): String {
        val file = location.file.absolutePath
        val start = location.start
        val end = location.end
        val sLine = start?.line ?: -1
        val sCol = start?.column ?: -1
        val sOff = start?.offset ?: -1
        val eLine = end?.line ?: -1
        val eCol = end?.column ?: -1
        val eOff = end?.offset ?: -1
        return "$file;$sLine;$sCol;$sOff;$eLine;$eCol;$eOff"
    }

    private fun deserializeLocation(serialized: String): Location? {
        val parts = serialized.split(";")
        if (parts.size != 7) return null
        val file = File(parts[0])
        val sLine = parts[1].toIntOrNull() ?: return null
        val sCol = parts[2].toIntOrNull() ?: return null
        val sOff = parts[3].toIntOrNull() ?: return null
        val eLine = parts[4].toIntOrNull() ?: return null
        val eCol = parts[5].toIntOrNull() ?: return null
        val eOff = parts[6].toIntOrNull() ?: return null

        val startPos = DefaultPosition(sLine, sCol, sOff)
        val endPos = DefaultPosition(eLine, eCol, eOff)
        return Location.create(file, startPos, endPos)
    }
}