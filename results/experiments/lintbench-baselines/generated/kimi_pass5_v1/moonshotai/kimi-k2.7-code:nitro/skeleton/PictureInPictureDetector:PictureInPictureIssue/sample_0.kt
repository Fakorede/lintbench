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

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val METHODS_KEY = "methods"
        private const val ENABLE_LOC_KEY = "enableLoc"

        private val RELEVANT_METHODS = listOf(
            "enterPictureInPictureMode",
            "setAutoEnterEnabled",
            "setSourceRectHint",
        )

        private val PIP_BUILDER_CLASSES = setOf(
            "android.app.PictureInPictureParams$Builder",
            "android.app.PictureInPictureParams.Builder",
        )

        private const val ACTIVITY_CLASS = "android.app.Activity"

        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the recommended approach for picture-in-picture (PiP)
                has changed. To provide a high-quality PiP transition animation, you should call
                PictureInPictureParams.Builder.setAutoEnterEnabled(true) and
                PictureInPictureParams.Builder.setSourceRectHint(...) when enabling PiP.
                Apps that do not use these APIs may have lower-quality transitions.
                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = RELEVANT_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val name = node.methodName ?: return
        val className = method.containingClass?.qualifiedName ?: return

        when (name) {
            "enterPictureInPictureMode" -> {
                if (className == ACTIVITY_CLASS) {
                    recordCall(context, node, name)
                }
            }
            "setAutoEnterEnabled" -> {
                if (className in PIP_BUILDER_CLASSES) {
                    val arg = node.valueArguments.firstOrNull()
                    if (arg is ULiteralExpression && arg.value == false) {
                        return
                    }
                    recordCall(context, node, name)
                }
            }
            "setSourceRectHint" -> {
                if (className in PIP_BUILDER_CLASSES) {
                    recordCall(context, node, name)
                }
            }
        }
    }

    private fun recordCall(context: JavaContext, node: UCallExpression, name: String) {
        val root = context.getPartialResults(ISSUE)
        val fileResult = root.map().getOrPut(context.file.path) { PartialResult() }

        val methods = fileResult.getString(METHODS_KEY) ?: ""
        fileResult.setString(
            METHODS_KEY,
            if (methods.isEmpty()) name else "$methods,$name",
        )

        if (name == "enterPictureInPictureMode" || name == "setAutoEnterEnabled") {
            if (fileResult.getString(ENABLE_LOC_KEY).isNullOrEmpty()) {
                val identifier = node.methodIdentifier?.sourcePsi
                val start = identifier?.textOffset ?: node.sourcePsi?.textOffset ?: 0
                val end = identifier?.textRange?.endOffset ?: (start + name.length)
                fileResult.setString(ENABLE_LOC_KEY, "$start:$end")
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Project-level aggregation is handled in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasEnable = false
        var hasAutoEnter = false
        var hasSourceRect = false
        var reportPath: String? = null
        var reportLocation: String? = null

        for ((path, fileResult) in partialResults.map()) {
            val called = fileResult.getString(METHODS_KEY)?.split(",") ?: emptyList()

            if (called.contains("enterPictureInPictureMode") || called.contains("setAutoEnterEnabled")) {
                hasEnable = true
                if (reportPath == null) {
                    reportPath = path
                    reportLocation = fileResult.getString(ENABLE_LOC_KEY)
                }
            }
            if (called.contains("setAutoEnterEnabled")) {
                hasAutoEnter = true
            }
            if (called.contains("setSourceRectHint")) {
                hasSourceRect = true
            }
        }

        if (!hasEnable || (hasAutoEnter && hasSourceRect)) {
            return
        }

        val missing = buildList {
            if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) add("setSourceRectHint(...)")
        }

        val message =
            "Picture In Picture best practices not followed. " +
                "When enabling PiP on Android 12+ you should call " +
                missing.joinToString(" and ") + "."

        val path = reportPath ?: return
        val location = if (reportLocation != null) {
            val parts = reportLocation.split(":").mapNotNull { it.toIntOrNull() }
            if (parts.size == 2 && parts[1] > 0) {
                Location.create(java.io.File(path), parts[0], parts[1])
            } else {
                Location.create(java.io.File(path))
            }
        } else {
            Location.create(java.io.File(path))
        }

        context.report(ISSUE, location, message)
    }
}