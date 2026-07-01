package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"

        private const val KEY_HAS_AUTO_ENTER = "hasAutoEnterEnabled"
        private const val KEY_HAS_SOURCE_RECT_HINT = "hasSourceRectHint"
        private const val KEY_LOCATION = "location"
        private const val KEY_MESSAGE = "message"

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's \
                transition animations will be of poor quality compared to other apps. The new \
                approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
        )

        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"
    }

    /**
     * Per-project tracking of whether we've seen setAutoEnterEnabled and setSourceRectHint calls.
     */
    private var hasAutoEnterEnabled = false
    private var hasSourceRectHint = false

    /** Location info for the first `build()` call we see, used for reporting. */
    private var buildCallLocation: com.android.tools.lint.detector.api.Location? = null
    private var buildCallMessage: String? = null

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_BUILD
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    // Check that the argument is `true`
                    val arg = node.valueArguments.firstOrNull()
                    if (arg != null) {
                        val value = arg.evaluate()
                        if (value == true) {
                            hasAutoEnterEnabled = true
                        }
                    }
                }
            }

            METHOD_SET_SOURCE_RECT_HINT -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    hasSourceRectHint = true
                }
            }

            METHOD_BUILD -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    if (buildCallLocation == null) {
                        buildCallLocation = context.getLocation(node)
                        buildCallMessage = buildMessage()
                    }
                }
            }
        }
    }

    private fun buildMessage(): String {
        return "PictureInPictureParams.Builder is missing recommended best-practice calls: " +
            "ensure you call both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
            "for smoother transitions starting in Android 12."
    }

    override fun afterCheckEachProject(context: Context) {
        if (!context.isGlobalAnalysis()) {
            // Partial analysis: store our findings in the partial results map
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_HAS_AUTO_ENTER, hasAutoEnterEnabled)
            map.put(KEY_HAS_SOURCE_RECT_HINT, hasSourceRectHint)

            val loc = buildCallLocation
            if (loc != null) {
                map.put(KEY_LOCATION, loc)
                map.put(KEY_MESSAGE, buildCallMessage ?: buildMessage())
            }
        } else {
            // Global analysis: report directly
            reportIfNeeded(context)
        }

        // Reset state for next project
        hasAutoEnterEnabled = false
        hasSourceRectHint = false
        buildCallLocation = null
        buildCallMessage = null
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var combinedHasAutoEnter = false
        var combinedHasSourceRectHint = false
        var reportLocation: com.android.tools.lint.detector.api.Location? = null
        var reportMessage: String? = null

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_HAS_AUTO_ENTER) == true) {
                combinedHasAutoEnter = true
            }
            if (map.getBoolean(KEY_HAS_SOURCE_RECT_HINT) == true) {
                combinedHasSourceRectHint = true
            }
            if (reportLocation == null) {
                val loc = map.getLocation(KEY_LOCATION)
                if (loc != null) {
                    reportLocation = loc
                    reportMessage = map.getString(KEY_MESSAGE, null)
                }
            }
        }

        val missingAutoEnter = !combinedHasAutoEnter
        val missingSourceRectHint = !combinedHasSourceRectHint

        if ((missingAutoEnter || missingSourceRectHint) && reportLocation != null) {
            val message = buildSpecificMessage(missingAutoEnter, missingSourceRectHint)
            context.report(
                Incident(
                    ISSUE,
                    reportLocation,
                    message
                )
            )
        }
    }

    private fun reportIfNeeded(context: Context) {
        val missingAutoEnter = !hasAutoEnterEnabled
        val missingSourceRectHint = !hasSourceRectHint

        val loc = buildCallLocation ?: return

        if (missingAutoEnter || missingSourceRectHint) {
            val message = buildSpecificMessage(missingAutoEnter, missingSourceRectHint)
            context.report(
                Incident(
                    ISSUE,
                    loc,
                    message
                )
            )
        }
    }

    private fun buildSpecificMessage(missingAutoEnter: Boolean, missingSourceRectHint: Boolean): String {
        return when {
            missingAutoEnter && missingSourceRectHint ->
                "PictureInPictureParams.Builder should call both `setAutoEnterEnabled(true)` " +
                    "and `setSourceRectHint(...)` for smoother PiP transitions on Android 12+. " +
                    "See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"

            missingAutoEnter ->
                "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` " +
                    "for smoother PiP transitions on Android 12+. " +
                    "See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"

            else ->
                "PictureInPictureParams.Builder should call `setSourceRectHint(...)` " +
                    "for smoother PiP transitions on Android 12+. " +
                    "See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
        }
    }
}