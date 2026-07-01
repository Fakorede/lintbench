package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val USES_PIP = "usesPip"
        private const val AUTO_ENTER = "autoEnter"
        private const val SOURCE_RECT_HINT = "sourceRectHint"
        private const val PATH = "path"
        private const val START = "start"
        private const val END = "end"

        private const val MESSAGE =
            "Picture In Picture best practices not followed: " +
                "call setAutoEnterEnabled(true) and setSourceRectHint(...) on the PictureInPictureParams.Builder."

        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the recommended picture-in-picture behavior
                has changed. For the smoothest PiP transition and best user experience, build
                your `PictureInPictureParams` with `PictureInPictureParams.Builder` and call
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` before passing the
                params to `setPictureInPictureParams(...)` or `enterPictureInPictureMode(...)`.

                Without these calls your PiP transition may look poor compared to other apps.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun supportsPartialResults(): Boolean = true

    override fun getApplicableMethodNames(): List<String> = listOf(
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
        if (context.mainProject.minSdk < 31) {
            return
        }

        val className = node.getParentOfType(UClass::class.java)?.qualifiedName ?: return

        val partial = context.getPartialResults(ISSUE)
        val current = partial.getMap()?.toMutableMap() ?: mutableMapOf<String, Any>()
        @Suppress("UNCHECKED_CAST")
        val classMap = (current[className] as? Map<String, Any>)?.toMutableMap()
            ?: mutableMapOf<String, Any>()

        when (method.name) {
            "enterPictureInPictureMode",
            "setPictureInPictureParams" -> {
                classMap[USES_PIP] = true
                val psi = node.sourcePsiElement
                if (psi != null) {
                    classMap[PATH] = context.file.absolutePath
                    classMap[START] = psi.textRange.startOffset
                    classMap[END] = psi.textRange.endOffset
                }
            }

            "setAutoEnterEnabled" -> {
                val arg: UExpression? = node.valueArguments.firstOrNull()
                val value = arg?.evaluate()
                classMap[AUTO_ENTER] = value != false
            }

            "setSourceRectHint" -> {
                if (node.valueArguments.isNotEmpty()) {
                    classMap[SOURCE_RECT_HINT] = true
                }
            }
        }

        current[className] = classMap
        partial.setMap(current)
    }

    override fun afterCheckEachProject(context: Context) {
        processPartialResults(context, context.getPartialResults(ISSUE))
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        processPartialResults(context, partialResults)
    }

    private fun processPartialResults(context: Context, partial: PartialResult) {
        val maps = partial.getMaps().values.takeUnless { it.isEmpty() } ?: listOf(partial)
        val combined = mutableMapOf<String, MutableMap<String, Any>>()

        for (sub in maps) {
            val map = sub.getMap() ?: continue
            for ((className, raw) in map) {
                val entry = raw as? Map<String, Any> ?: continue
                val existing = combined.getOrPut(className as String) { mutableMapOf() }

                if (entry[USES_PIP] == true) existing[USES_PIP] = true
                if (entry[AUTO_ENTER] == true) existing[AUTO_ENTER] = true
                if (entry[SOURCE_RECT_HINT] == true) existing[SOURCE_RECT_HINT] = true

                if (existing[PATH] == null && entry[PATH] != null) {
                    existing[PATH] = entry.getValue(PATH)
                    existing[START] = entry.getValue(START)
                    existing[END] = entry.getValue(END)
                }
            }
        }

        for (data in combined.values) {
            if (data[USES_PIP] != true) continue

            val autoEnter = data[AUTO_ENTER] == true
            val sourceRectHint = data[SOURCE_RECT_HINT] == true

            if (!autoEnter || !sourceRectHint) {
                val path = data[PATH] as? String
                val start = (data[START] as? Number)?.toInt()
                val end = (data[END] as? Number)?.toInt()
                val location = if (path != null && start != null && end != null) {
                    Location.create(java.io.File(path), start, end)
                } else {
                    Location.create(context.file)
                }
                context.report(ISSUE, location, MESSAGE)
            }
        }
    }
}