package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val KEY_HAS_PIP_CALL = "hasPipCall"
        private const val KEY_AUTO_ENTER = "autoEnter"
        private const val KEY_SOURCE_RECT_HINT = "sourceRectHint"
        private const val KEY_CALL_COUNT = "callCount"
        private const val KEY_START_LINE = "startLine_"
        private const val KEY_END_LINE = "endLine_"

        private const val ACTIVITY = "android.app.Activity"
        private const val BUILDER = "android.app.PictureInPictureParams.Builder"

        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the picture-in-picture transition behavior has changed.
                For a smooth, high-quality transition into and out of PiP, you should configure the
                <code>PictureInPictureParams.Builder</code> by calling
                <code>setAutoEnterEnabled(true)</code> and <code>setSourceRectHint(Rect)</code>,
                then pass the built params to <code>Activity.setPictureInPictureParams()</code> or
                <code>Activity.enterPictureInPictureMode(params)</code>.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "enterPictureInPictureMode",
        "setPictureInPictureParams",
        "setAutoEnterEnabled",
        "setSourceRectHint",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val partial = context.getPartialResults(ISSUE)
        val topMap = partial.map()
        val fileMap = topMap.getMap(context.file.path) ?: LintMap()

        when (method.name) {
            "enterPictureInPictureMode", "setPictureInPictureParams" -> {
                if (method.containingClass?.qualifiedName == ACTIVITY) {
                    val location = context.getLocation(node)
                    val count = fileMap.getInt(KEY_CALL_COUNT, 0)
                    fileMap.putInt(KEY_START_LINE + count, location.start?.line ?: -1)
                    fileMap.putInt(KEY_END_LINE + count, location.end?.line ?: -1)
                    fileMap.putInt(KEY_CALL_COUNT, count + 1)
                    fileMap.putBoolean(KEY_HAS_PIP_CALL, true)
                }
            }
            "setAutoEnterEnabled" -> {
                if (method.containingClass?.qualifiedName == BUILDER) {
                    val arg = node.valueArguments.firstOrNull()
                    if (arg?.evaluate() == true) {
                        fileMap.putBoolean(KEY_AUTO_ENTER, true)
                    }
                }
            }
            "setSourceRectHint" -> {
                if (method.containingClass?.qualifiedName == BUILDER) {
                    if (node.valueArguments.isNotEmpty()) {
                        fileMap.putBoolean(KEY_SOURCE_RECT_HINT, true)
                    }
                }
            }
        }

        topMap.putMap(context.file.path, fileMap)
        partial.setMap(topMap)
    }

    override fun afterCheckEachProject(context: Context) {
        // No project-level cleanup is needed when using partial results.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val topMap = partialResults.map()
        for (path in topMap.keyNames) {
            val fileMap = topMap.getMap(path) ?: continue
            if (!fileMap.getBoolean(KEY_HAS_PIP_CALL, false)) continue

            val hasAutoEnter = fileMap.getBoolean(KEY_AUTO_ENTER, false)
            val hasSourceRectHint = fileMap.getBoolean(KEY_SOURCE_RECT_HINT, false)
            if (hasAutoEnter && hasSourceRectHint) continue

            val missing = buildList {
                if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
                if (!hasSourceRectHint) add("setSourceRectHint(Rect)")
            }.joinToString(" and ")

            val count = fileMap.getInt(KEY_CALL_COUNT, 0)
            val file = java.io.File(path)
            for (i in 0 until count) {
                val startLine = fileMap.getInt(KEY_START_LINE + i, -1)
                val endLine = fileMap.getInt(KEY_END_LINE + i, -1)
                if (startLine < 0 || endLine < 0) continue

                val location = context.getRangeLocation(file, startLine, 0, endLine, 0)
                context.report(
                    ISSUE,
                    location,
                    "Picture-in-Picture best practices not followed: also call $missing on PictureInPictureParams.Builder for a smoother transition on Android 12+.",
                )
            }
        }
    }
}