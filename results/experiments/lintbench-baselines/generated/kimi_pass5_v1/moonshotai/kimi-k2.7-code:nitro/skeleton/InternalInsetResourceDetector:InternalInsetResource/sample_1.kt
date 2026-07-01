package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources (such as \
                `android.R.dimen.status_bar_height`, \
                `android.R.dimen.navigation_bar_height`, and similar \
                resources) are not a supported way to retrieve the relevant \
                insets for your application. The insets are dynamic values that \
                can change while your app is visible, and your app's window may \
                not intersect with the system UI. To get the relevant value for \
                your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("getDimension", "getDimensionPixelOffset", "getDimensionPixelSize")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "android.content.res.Resources") {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        if (!isInternalInsetResource(argument, context)) {
            return
        }

        val field = context.evaluator.resolve(argument) as? PsiField ?: return
        val message = "Using internal inset dimension resource (${field.name}); " +
                "use WindowInsetsCompat instead"
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun isInternalInsetResource(
        argument: UExpression, context: JavaContext,
    ): Boolean {
        val field = context.evaluator.resolve(argument) as? PsiField ?: return false
        val containingClass = field.containingClass ?: return false
        return containingClass.qualifiedName == "android.R.dimen" &&
                field.name in INSET_RESOURCES
    }
}