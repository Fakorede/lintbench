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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val DIMENSION_METHODS = listOf(
            "getDimension",
            "getDimensionPixelOffset",
            "getDimensionPixelSize",
        )

        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_portrait",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_width_portrait",
            "navigation_bar_width_landscape",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve
                the relevant insets for your application. The insets are dynamic values that
                can change while your app is visible, and your app's window may not intersect
                with the system UI. To get the relevant value for your app and listen to updates,
                use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = DIMENSION_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val resourceName = getDimensionResourceName(node.valueArguments.firstOrNull() ?: return)
            ?: return
        if (resourceName !in INSET_RESOURCE_NAMES) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource is not recommended; " +
                "use WindowInsetsCompat to retrieve insets and listen for changes",
        )
    }

    private fun getDimensionResourceName(expression: UExpression): String? {
        val ref = expression as? UQualifiedReferenceExpression ?: return null
        val selector = ref.selector as? USimpleNameReferenceExpression ?: return null

        val receiver = ref.receiver as? UQualifiedReferenceExpression ?: return null
        val typeSelector = receiver.selector as? USimpleNameReferenceExpression ?: return null
        if (typeSelector.identifier != "dimen") {
            return null
        }

        return selector.identifier
    }
}