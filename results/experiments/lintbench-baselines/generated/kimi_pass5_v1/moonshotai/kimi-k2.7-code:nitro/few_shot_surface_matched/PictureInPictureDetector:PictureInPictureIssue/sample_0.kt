package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.io.File
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANDROID_12: Int = 31
        private const val BUILDER_CLASS: String = "android.app.PictureInPictureParams.Builder"
        private const val ISSUE_ID: String = "PictureInPictureIssue"

        @JvmField
        val PICTURE_IN_PICTURE_ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31+), the recommended approach for enabling picture-in-picture (PiP) has changed. To achieve smooth transition animations, configure your `PictureInPictureParams.Builder` with both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            CONSTRUCTOR_NAME,
            "setAutoEnterEnabled",
            "setSourceRectHint",
            "build"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, BUILDER_CLASS)) {
            return
        }

        val partialResults = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE)
        val maps = partialResults.maps()

        if (method.isConstructor) {
            val rootKey = computeRootKey(context, node)
            val existing = maps[rootKey]
            val map = existing ?: LintMap.empty()
            map.putString("file", context.file.path)
            map.putInt("startOffset", node.sourcePsi?.textOffset ?: 0)
            map.putInt("endOffset", (node.sourcePsi?.textOffset ?: 0) + (node.sourcePsi?.textLength ?: 0))
            map.putBoolean("hasAutoEnterEnabled", existing?.getBoolean("hasAutoEnterEnabled", false) ?: false)
            map.putBoolean("hasSourceRectHint", existing?.getBoolean("hasSourceRectHint", false) ?: false)
            maps[rootKey] = map
        } else {
            val root = findBuilderRoot(context, node) ?: return
            val rootKey = computeRootKey(context, root)
            val existing = maps[rootKey] ?: return
            val map = existing
            when (method.name) {
                "setAutoEnterEnabled" -> {
                    val argument = node.valueArguments.firstOrNull()
                    val enabled = argument != null && ConstantEvaluator.evaluate(context, argument) == true
                    map.putBoolean("hasAutoEnterEnabled", enabled)
                }
                "setSourceRectHint" -> {
                    val argument = node.valueArguments.firstOrNull()
                    if (argument != null) {
                        map.putBoolean("hasSourceRectHint", true)
                    }
                }
            }
            maps[rootKey] = map
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Consolidation is handled by Lint's map merging; no additional work needed.
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.project.buildTargetSdk() > 0 && context.project.buildTargetSdk() < ANDROID_12) {
            return
        }

        val partialResults = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE)
        val maps = partialResults.maps()

        for ((_, map) in maps) {
            val hasAutoEnterEnabled = map.getBoolean("hasAutoEnterEnabled", false)
            val hasSourceRectHint = map.getBoolean("hasSourceRectHint", false)
            if (!hasAutoEnterEnabled || !hasSourceRectHint) {
                val filePath = map.getString("file", null) ?: continue
                val startOffset = map.getInt("startOffset", 0)
                val endOffset = map.getInt("endOffset", 0)
                val file = File(filePath)
                val location = Location.create(file, startOffset, endOffset)
                val missing = buildList {
                    if (!hasAutoEnterEnabled) add("setAutoEnterEnabled(true)")
                    if (!hasSourceRectHint) add("setSourceRectHint(...)")
                }
                val message = "PictureInPictureParams.Builder is missing recommended PiP configuration: ${missing.joinToString()}."
                context.report(Incident(PICTURE_IN_PICTURE_ISSUE, location, message))
            }
        }
    }

    private fun findBuilderRoot(context: JavaContext, node: UCallExpression): UCallExpression? {
        var current: UExpression? = node
        while (current is UCallExpression) {
            val resolved = current.resolve()
            if (resolved != null && context.evaluator.isMemberInClass(resolved, BUILDER_CLASS)) {
                if (current.isConstructorCall) {
                    return current
                }
            }
            current = current.receiver
        }
        return null
    }

    private fun computeRootKey(context: JavaContext, node: UCallExpression): String {
        return "${context.file.path}:${node.sourcePsi?.textOffset ?: node.hashCode()}"
    }
}