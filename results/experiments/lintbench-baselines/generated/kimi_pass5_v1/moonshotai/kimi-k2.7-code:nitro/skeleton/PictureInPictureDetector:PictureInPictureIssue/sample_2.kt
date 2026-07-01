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
import org.jetbrains.uast.ULiteralExpression

private const val PARTIAL_RESULT_KEY = "pip"
private const val PREFIX_ENTER = "enter:"
private const val PREFIX_AUTO = "auto:"
private const val PREFIX_SOURCE = "source:"

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the recommended approach for enabling
                picture-in-picture (PiP) has changed. To provide smooth, high-quality
                PiP transition animations, you should call `setAutoEnterEnabled(true)` and
                `setSourceRectHint(...)` on `PictureInPictureParams.Builder` when entering
                or configuring PiP mode.
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
        val partialResult = context.getPartialResult(PARTIAL_RESULT_KEY) ?: return
        val map = partialResult.maps() as? MutableMap<String, Location> ?: return
        val name = method.name
        val location = context.getLocation(node)
        val file = context.file.path
        val offset = node.sourcePsiElement?.textOffset ?: 0

        when (name) {
            "enterPictureInPictureMode", "setPictureInPictureParams" -> {
                map["$PREFIX_ENTER$file:$offset"] = location
            }
            "setAutoEnterEnabled" -> {
                val arg = node.valueArguments.firstOrNull()
                if (arg is ULiteralExpression && arg.value == true) {
                    map["$PREFIX_AUTO$file:$offset"] = location
                }
            }
            "setSourceRectHint" -> {
                map["$PREFIX_SOURCE$file:$offset"] = location
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No per-project cleanup needed; reporting happens in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val map = partialResults.maps() ?: return
        var hasEnterUsage = false
        var hasAutoEnter = false
        var hasSourceRectHint = false
        val enterLocations = mutableListOf<Location>()

        for ((key, location) in map) {
            when {
                key.startsWith(PREFIX_ENTER) -> {
                    hasEnterUsage = true
                    enterLocations.add(location)
                }
                key.startsWith(PREFIX_AUTO) -> hasAutoEnter = true
                key.startsWith(PREFIX_SOURCE) -> hasSourceRectHint = true
            }
        }

        if (hasEnterUsage && (!hasAutoEnter || !hasSourceRectHint)) {
            val message = "For smoother picture-in-picture transitions on Android 12+ (API 31+), " +
                    "call setAutoEnterEnabled(true) and setSourceRectHint(...) on " +
                    "PictureInPictureParams.Builder."
            for (loc in enterLocations) {
                context.report(ISSUE, loc, message)
            }
        }
    }
}