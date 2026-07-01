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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.ULiteralExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val METHOD_ENTER_PIP = "enterPictureInPictureMode"
        private const val METHOD_SET_AUTO_ENTER = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"

        private const val CLASS_ACTIVITY = "android.app.Activity"
        private const val CLASS_PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams$Builder"

        private const val KEY_AUTO_ENTER = "autoEnterEnabled"
        private const val KEY_SOURCE_RECT_HINT = "sourceRectHint"
        private const val KEY_ENTER_CALLS = "enterCalls"
        private const val KEY_PATH = "path"
        private const val KEY_START = "start"
        private const val KEY_END = "end"

        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), picture-in-picture (PiP) transitions are
                smoothest when you call PictureInPictureParams.Builder.setAutoEnterEnabled(true)
                and PictureInPictureParams.Builder.setSourceRectHint(...) before the activity
                enters PiP. Apps that continue to use the older approach (for example, calling
                enterPictureInPictureMode without these settings) will have lower-quality
                transition animations.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private data class CallRecord(val path: String, val startOffset: Int, val endOffset: Int)

    private val enterCalls = mutableListOf<CallRecord>()
    private var foundAutoEnter = false
    private var foundSourceRectHint = false

    override fun getApplicableMethodNames(): List<String>? = listOf(
        METHOD_ENTER_PIP,
        METHOD_SET_AUTO_ENTER,
        METHOD_SET_SOURCE_RECT_HINT,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val owner = method.containingClass?.qualifiedName ?: return
        when (method.name) {
            METHOD_ENTER_PIP -> {
                if (owner == CLASS_ACTIVITY) {
                    val location = context.getLocation(node)
                    val startOffset = location.start?.offset ?: -1
                    val endOffset = location.end?.offset ?: -1
                    if (startOffset >= 0 && endOffset >= 0) {
                        enterCalls.add(CallRecord(context.file.path, startOffset, endOffset))
                    }
                }
            }
            METHOD_SET_AUTO_ENTER -> {
                if (owner == CLASS_PIP_PARAMS_BUILDER &&
                    isTrueLiteral(node.valueArguments.firstOrNull())) {
                    foundAutoEnter = true
                }
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                if (owner == CLASS_PIP_PARAMS_BUILDER) {
                    foundSourceRectHint = true
                }
            }
        }
    }

    private fun isTrueLiteral(expr: UExpression?): Boolean {
        val actual = if (expr is UNamedExpression) expr.expression else expr
        return actual is ULiteralExpression && actual.value == true
    }

    override fun afterCheckEachProject(context: Context) {
        val map = context.getPartialResults(ISSUE).map() ?: return
        map.addProperty(KEY_AUTO_ENTER, foundAutoEnter)
        map.addProperty(KEY_SOURCE_RECT_HINT, foundSourceRectHint)

        val callsArray = com.google.gson.JsonArray()
        for (call in enterCalls) {
            val callMap = com.google.gson.JsonObject()
            callMap.addProperty(KEY_PATH, call.path)
            callMap.addProperty(KEY_START, call.startOffset)
            callMap.addProperty(KEY_END, call.endOffset)
            callsArray.add(callMap)
        }
        map.add(KEY_ENTER_CALLS, callsArray)

        enterCalls.clear()
        foundAutoEnter = false
        foundSourceRectHint = false
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasAutoEnter = false
        var hasSourceRectHint = false
        val allCalls = mutableListOf<CallRecord>()

        val summaries = listOfNotNull(partialResults.map(), partialResults.parentMap())
        for (summary in summaries) {
            if (summary.has(KEY_AUTO_ENTER) && summary.get(KEY_AUTO_ENTER).asBoolean) {
                hasAutoEnter = true
            }
            if (summary.has(KEY_SOURCE_RECT_HINT) && summary.get(KEY_SOURCE_RECT_HINT).asBoolean) {
                hasSourceRectHint = true
            }

            val callsElement = summary.get(KEY_ENTER_CALLS)
            if (callsElement != null && callsElement.isJsonArray) {
                for (element in callsElement.asJsonArray) {
                    if (element.isJsonObject) {
                        val callMap = element.asJsonObject
                        val path = callMap.get(KEY_PATH).asString
                        val start = callMap.get(KEY_START).asInt
                        val end = callMap.get(KEY_END).asInt
                        allCalls.add(CallRecord(path, start, end))
                    }
                }
            }
        }

        if (allCalls.isEmpty()) return
        if (hasAutoEnter && hasSourceRectHint) return

        val missing = buildString {
            val missingAutoEnter = !hasAutoEnter
            val missingSourceRect = !hasSourceRectHint
            if (missingAutoEnter) {
                append("PictureInPictureParams.Builder.setAutoEnterEnabled(true)")
            }
            if (missingAutoEnter && missingSourceRect) {
                append(" and ")
            }
            if (missingSourceRect) {
                append("PictureInPictureParams.Builder.setSourceRectHint(...)")
            }
        }

        val message = "For smoother picture-in-picture transitions on Android 12+, also call $missing."

        for (call in allCalls) {
            val file = java.io.File(call.path)
            val contents = context.getFileContents(file) ?: continue
            val location = Location.create(file, contents, call.startOffset, call.endOffset)
            context.report(ISSUE, location, message)
        }
    }
}