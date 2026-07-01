package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

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

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).setAndroidSpecific(true)

        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val BUILD_METHOD = "build"
        private const val ENTER_PIP_METHOD = "enterPictureInPictureMode"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(BUILD_METHOD, ENTER_PIP_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        if (methodName == BUILD_METHOD) {
            val containingClass = method.containingClass ?: return
            if (containingClass.qualifiedName != PIP_PARAMS_BUILDER) return

            val enclosingMethod = node.getParentOfType<UMethod>(strict = true) ?: return
            val callsInMethod = collectMethodCallNames(enclosingMethod)

            val hasAutoEnter = callsInMethod.contains(SET_AUTO_ENTER_ENABLED)
            val hasSourceRectHint = callsInMethod.contains(SET_SOURCE_RECT_HINT)

            if (!hasAutoEnter && !hasSourceRectHint) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` " +
                        "and `setSourceRectHint(...)` for smoother transitions on Android 12+"
                )
            } else if (!hasAutoEnter) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` " +
                        "for smoother transitions on Android 12+"
                )
            } else if (!hasSourceRectHint) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "PictureInPictureParams.Builder should call `setSourceRectHint(...)` " +
                        "for smoother transitions on Android 12+"
                )
            }
        } else if (methodName == ENTER_PIP_METHOD) {
            val enclosingMethod = node.getParentOfType<UMethod>(strict = true) ?: return
            val callsInMethod = collectMethodCallNames(enclosingMethod)

            if (!callsInMethod.contains(BUILD_METHOD)) return

            val hasAutoEnter = callsInMethod.contains(SET_AUTO_ENTER_ENABLED)
            val hasSourceRectHint = callsInMethod.contains(SET_SOURCE_RECT_HINT)

            if (!hasAutoEnter && !hasSourceRectHint) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "When calling `enterPictureInPictureMode`, the PictureInPictureParams " +
                        "should be built with `setAutoEnterEnabled(true)` and " +
                        "`setSourceRectHint(...)` for smoother transitions on Android 12+"
                )
            }
        }
    }

    private fun collectMethodCallNames(method: UMethod): Set<String> {
        val names = mutableSetOf<String>()
        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                node.methodName?.let { names.add(it) }
                return false
            }
        })
        return names
    }
}