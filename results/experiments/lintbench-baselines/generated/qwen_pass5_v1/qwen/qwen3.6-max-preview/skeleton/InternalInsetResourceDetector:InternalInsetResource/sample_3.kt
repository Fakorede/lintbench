package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceType
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

        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "status_bar_height_landscape",
            "navigation_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height_portrait"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = "The internal inset dimension resources are not a supported way to " +
                "retrieve the relevant insets for your application. The insets are " +
                "dynamic values that can change while your app is visible, and your " +
                "app's window may not intersect with the system UI. " +
                "To get the relevant value for your app and listen to updates, use " +
                "`androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getDimension",
        "getDimensionPixelSize",
        "getDimensionPixelOffset"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val arg = node.valueArguments.firstOrNull() ?: return
        val resourceUrl = context.getResourceId(arg) ?: return

        if (resourceUrl.type == ResourceType.DIMEN && resourceUrl.name in INTERNAL_INSET_DIMENS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `WindowInsetsCompat` instead of internal inset dimension resources"
            )
        }
    }
}