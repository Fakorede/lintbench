package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Location
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass

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
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
                    "has changed. If your app does not use the new approach, your app's transition animations " +
                    "will be of poor quality compared to other apps. The new approach requires calling " +
                    "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private class PiPState(
        val className: String,
        val setParamsCalls: MutableList<Location> = mutableListOf(),
        val enterPiPCalls: MutableList<Location> = mutableListOf(),
        var hasAutoEnterTrue: Boolean = false,
        var hasSourceRectHint: Boolean = false
    )

    private val classStates = mutableMapOf<String, PiPState>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setAutoEnterEnabled",
        "setSourceRectHint",
        "setPictureInPictureParams",
        "enterPictureInPictureMode"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val methodName = method.name
        val containingClass = node.getContainingUClass()?.qualifiedName ?: context.file.absolutePath
        val state = classStates.getOrPut(containingClass) { PiPState(containingClass) }

        when (methodName) {
            "setAutoEnterEnabled" -> {
                val firstArg = node.valueArguments.firstOrNull()
                if (firstArg != null) {
                    val evaluated = firstArg.evaluate()
                    if (evaluated == true || firstArg.asSourceString() == "true") {
                        state.hasAutoEnterTrue = true
                    }
                }
            }
            "setSourceRectHint" -> {
                state.hasSourceRectHint = true
            }
            "setPictureInPictureParams" -> {
                state.setParamsCalls.add(context.getLocation(node))
            }
            "enterPictureInPictureMode" -> {
                state.enterPiPCalls.add(context.getLocation(node))
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.phase == 1) {
            for ((_, state) in classStates) {
                val hasAutoEnter = state.hasAutoEnterTrue
                val hasSourceRect = state.hasSourceRectHint
                if (!hasAutoEnter || !hasSourceRect) {
                    val missing = mutableListOf<String>()
                    if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
                    if (!hasSourceRect) missing.add("setSourceRectHint(...)")

                    val missingStr = missing.joinToString(" and ")
                    val message = "To support smooth picture-in-picture transitions on Android 12 and higher, " +
                            "you should call $missingStr."

                    for (location in state.enterPiPCalls) {
                        context.report(ISSUE, location, message)
                    }
                    for (location in state.setParamsCalls) {
                        context.report(ISSUE, location, message)
                    }
                }
            }
            classStates.clear()
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op for this check as it is class-local and handled in afterCheckEachProject
    }
}