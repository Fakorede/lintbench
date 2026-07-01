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
        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's transition \
                animations will be of poor quality compared to other apps. The new approach requires \
                calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PIP_PARAMS_BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"

        private const val KEY_HAS_AUTO_ENTER = "hasAutoEnter"
        private const val KEY_HAS_SOURCE_RECT_HINT = "hasSourceRectHint"
        private const val KEY_BUILD_LOCATION = "buildLocation"
        private const val KEY_BUILD_FILE = "buildFile"
        private const val KEY_BUILD_LINE = "buildLine"
        private const val KEY_BUILD_MESSAGE = "buildMessage"
    }

    /**
     * Tracks per-file whether we've seen setAutoEnterEnabled and setSourceRectHint calls
     * on PictureInPictureParams.Builder instances, as well as the location of build() calls.
     */
    private data class BuilderState(
        var hasAutoEnterEnabled: Boolean = false,
        var hasSourceRectHint: Boolean = false,
        var buildNode: UCallExpression? = null,
        var context: JavaContext? = null
    )

    // We track builder states per file (using file path as key)
    private val builderStates = mutableMapOf<String, MutableList<BuilderState>>()

    // Current active builder being constructed (heuristic: track the last seen builder chain)
    private val currentBuilders = mutableMapOf<String, BuilderState>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_BUILD,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != PIP_PARAMS_BUILDER_CLASS) return

        val filePath = context.file.path

        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                val builder = getOrCreateCurrentBuilder(filePath, context)
                builder.hasAutoEnterEnabled = true
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                val builder = getOrCreateCurrentBuilder(filePath, context)
                builder.hasSourceRectHint = true
            }
            METHOD_BUILD -> {
                val builder = getOrCreateCurrentBuilder(filePath, context)
                builder.buildNode = node
                builder.context = context

                // Save the completed builder state and start fresh
                val list = builderStates.getOrPut(filePath) { mutableListOf() }
                list.add(builder)
                currentBuilders.remove(filePath)
            }
        }
    }

    private fun getOrCreateCurrentBuilder(filePath: String, context: JavaContext): BuilderState {
        return currentBuilders.getOrPut(filePath) { BuilderState(context = context) }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            reportIssues(context)
        } else {
            // Partial analysis: store results in the lint map for later aggregation
            val map = context.getPartialResults(ISSUE).map()

            var index = 0
            for ((_, states) in builderStates) {
                for (state in states) {
                    val buildNode = state.buildNode ?: continue
                    val javaContext = state.context ?: continue

                    if (!state.hasAutoEnterEnabled || !state.hasSourceRectHint) {
                        val location = javaContext.getLocation(buildNode)
                        map.put("$KEY_HAS_AUTO_ENTER.$index", state.hasAutoEnterEnabled)
                        map.put("$KEY_HAS_SOURCE_RECT_HINT.$index", state.hasSourceRectHint)
                        map.put("$KEY_BUILD_FILE.$index", location.file.path)
                        map.put("$KEY_BUILD_LINE.$index", location.start?.line ?: 0)
                        map.put("$KEY_BUILD_MESSAGE.$index", buildMessage(state))
                        index++
                    }
                }
            }
            map.put("count", index)

            builderStates.clear()
            currentBuilders.clear()
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for ((_, map) in partialResults) {
            val count = map.getInt("count") ?: continue
            for (i in 0 until count) {
                val hasAutoEnter = map.getBoolean("$KEY_HAS_AUTO_ENTER.$i") ?: false
                val hasSourceRectHint = map.getBoolean("$KEY_HAS_SOURCE_RECT_HINT.$i") ?: false
                val filePath = map.getString("$KEY_BUILD_FILE.$i") ?: continue
                val line = map.getInt("$KEY_BUILD_LINE.$i") ?: 0
                val message = map.getString("$KEY_BUILD_MESSAGE.$i") ?: continue

                if (!hasAutoEnter || !hasSourceRectHint) {
                    val file = java.io.File(filePath)
                    val location = com.android.tools.lint.detector.api.Location.create(file)
                    context.report(ISSUE, location, message)
                }
            }
        }
    }

    private fun reportIssues(context: Context) {
        for ((_, states) in builderStates) {
            for (state in states) {
                val buildNode = state.buildNode ?: continue
                val javaContext = state.context ?: continue

                if (!state.hasAutoEnterEnabled || !state.hasSourceRectHint) {
                    val location = javaContext.getLocation(buildNode)
                    val message = buildMessage(state)
                    context.report(ISSUE, location, message)
                }
            }
        }
        builderStates.clear()
        currentBuilders.clear()
    }

    private fun buildMessage(state: BuilderState): String {
        return when {
            !state.hasAutoEnterEnabled && !state.hasSourceRectHint ->
                "PictureInPictureParams.Builder is missing both `setAutoEnterEnabled(true)` " +
                    "and `setSourceRectHint(...)` calls. Starting in Android 12, both are " +
                    "required for smooth PiP transition animations."
            !state.hasAutoEnterEnabled ->
                "PictureInPictureParams.Builder is missing a `setAutoEnterEnabled(true)` call. " +
                    "Starting in Android 12, this is required for smooth PiP transition animations."
            !state.hasSourceRectHint ->
                "PictureInPictureParams.Builder is missing a `setSourceRectHint(...)` call. " +
                    "Starting in Android 12, this is required for smooth PiP transition animations."
            else -> ""
        }
    }
}