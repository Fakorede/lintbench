package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the \
                relevant insets for your application. The insets are dynamic values that can \
                change while your app is visible, and your app's window may not intersect with \
                the system UI. To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) return

        val name = ConstantEvaluator.evaluateString(context, args[0], false) ?: return
        val type = ConstantEvaluator.evaluateString(context, args[1], false)
        val pkg = ConstantEvaluator.evaluateString(context, args[2], false)

        val isStatusBar = name == "status_bar_height" || name == "status_bar_height_landscape" || name == "status_bar_height_portrait"
        val isNavigationBar = name == "navigation_bar_height" || name == "navigation_bar_height_landscape" || name == "navigation_bar_height_portrait"
        val isTargetResource = isStatusBar || isNavigationBar

        val isInternal = (pkg == "android" && isTargetResource) ||
                (pkg == null && (name.startsWith("android:dimen/status_bar_height") || name.startsWith("android:dimen/navigation_bar_height"))) ||
                (pkg == null && type == "dimen" && (name.startsWith("android:status_bar_height") || name.startsWith("android:navigation_bar_height")))

        if (isInternal) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource"
            )
        }
    }
}