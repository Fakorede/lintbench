package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_portrait",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "system_bars_inclusive"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getDimension",
        "getDimensionPixelSize",
        "getDimensionPixelOffset"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val arg = node.valueArguments.firstOrNull() ?: return
        val resourceName = getDimensionResourceName(arg) ?: return
        if (!INTERNAL_INSET_RESOURCES.contains(resourceName)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            ISSUE.getExplanation(TextFormat.RAW),
        )
    }

    private fun getDimensionResourceName(arg: UExpression): String? {
        val reference = arg as? UReferenceExpression ?: return null
        val resolved = reference.resolve() as? PsiField ?: return null
        if (resolved.containingClass?.qualifiedName != "android.R.dimen") {
            return null
        }
        return resolved.name
    }
}