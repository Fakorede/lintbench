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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
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

    /**
     * Track calls to PictureInPictureParams.Builder methods within a method scope.
     * We look for calls to `build()` on PictureInPictureParams.Builder and check
     * whether `setAutoEnterEnabled` and `setSourceRectHint` were called in the same
     * enclosing method.
     */
    override fun getApplicableMethodNames(): List<String> {
        return listOf(BUILD_METHOD, ENTER_PIP_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        if (methodName == BUILD_METHOD) {
            // Check if this is PictureInPictureParams.Builder.build()
            val containingClass = method.containingClass ?: return
            if (containingClass.qualifiedName != PIP_PARAMS_BUILDER) return

            // Find the enclosing method to scan for sibling calls
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
            // Check if enterPictureInPictureMode is called with a PictureInPictureParams
            // that was built without setAutoEnterEnabled / setSourceRectHint.
            // We check the enclosing method for the presence of both required calls.
            val enclosingMethod = node.getParentOfType<UMethod>(strict = true) ?: return

            val callsInMethod = collectMethodCallNames(enclosingMethod)

            // Only flag if there's a Builder.build() call in the same method
            // (meaning they're constructing params inline)
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

    /**
     * Collects all method call names within the given method body.
     */
    private fun collectMethodCallNames(method: UMethod): Set<String> {
        val names = mutableSetOf<String>()
        method.accept(object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                node.methodName?.let { names.add(it) }
            }
        })
        return names
    }

    /**
     * We need to override getApplicableUastTypes to ensure visitCallExpression is called
     * via the handler approach if needed. However, since we use getApplicableMethodNames,
     * we rely on visitMethodCall instead.
     */
    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null
}