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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

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
                The internal inset dimension resources are not a supported way to
                retrieve the relevant insets for your application. The insets are
                dynamic values that can change while your app is visible, and your
                app's window may not intersect with the system UI. To get the relevant
                value for your app and listen to updates, use
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val RESOURCE_METHODS = listOf(
            "getDimension",
            "getDimensionPixelOffset",
            "getDimensionPixelSize",
        )

        private val TYPED_VALUE_METHODS = listOf(
            "complexToDimension",
            "complexToDimensionPixelOffset",
            "complexToDimensionPixelSize",
        )

        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val TYPED_VALUE_CLASS = "android.util.TypedValue"
        private const val DIMEN_CLASS = "android.R.dimen"
    }

    override fun getApplicableMethodNames(): List<String>? =
        RESOURCE_METHODS + TYPED_VALUE_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val methodName = method.name
        val containingClass = method.containingClass ?: return

        val isApplicable = when (methodName) {
            in RESOURCE_METHODS ->
                context.evaluator.extendsClass(containingClass, RESOURCES_CLASS, false)
            in TYPED_VALUE_METHODS ->
                context.evaluator.extendsClass(containingClass, TYPED_VALUE_CLASS, false)
            else -> false
        }

        if (!isApplicable) return

        val argument = node.valueArguments.firstOrNull() ?: return
        val fieldReference = when (argument) {
            is UQualifiedReferenceExpression -> argument.selector
            is USimpleNameReferenceExpression -> argument
            else -> return
        }

        val field = fieldReference.resolve() as? PsiField ?: return
        if (field.containingClass?.qualifiedName != DIMEN_CLASS) return

        val resourceName = field.name
        if (resourceName !in INTERNAL_INSET_RESOURCES) return

        context.report(
            ISSUE,
            node,
            context.getLocation(argument),
            "Using internal inset dimension resource `$resourceName` is not recommended; " +
                "use WindowInsetsCompat APIs instead",
        )
    }
}