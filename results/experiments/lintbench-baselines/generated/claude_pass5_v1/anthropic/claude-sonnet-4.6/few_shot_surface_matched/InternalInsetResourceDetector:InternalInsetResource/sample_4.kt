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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_frame_height",
            "status_bar_height_default",
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI.
                To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )

        private val GET_IDENTIFIER_METHOD = "getIdentifier"
        private val GET_DIMENSION_PIXEL_SIZE_METHOD = "getDimensionPixelSize"
        private val GET_DIMENSION_PIXEL_OFFSET_METHOD = "getDimensionPixelOffset"
        private val GET_DIMENSION_METHOD = "getDimension"

        private val APPLICABLE_METHODS = listOf(
            GET_IDENTIFIER_METHOD,
            GET_DIMENSION_PIXEL_SIZE_METHOD,
            GET_DIMENSION_PIXEL_OFFSET_METHOD,
            GET_DIMENSION_METHOD,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            GET_IDENTIFIER_METHOD -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) return
                val nameArg = node.valueArguments.getOrNull(0) ?: return
                val name = (nameArg as? ULiteralExpression)?.evaluateString()
                    ?: nameArg.evaluateString()
                    ?: return
                if (name in INSET_RESOURCE_NAMES) {
                    report(context, node, name)
                }
            }
            GET_DIMENSION_PIXEL_SIZE_METHOD,
            GET_DIMENSION_PIXEL_OFFSET_METHOD,
            GET_DIMENSION_METHOD -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) return
                val resIdArg = node.valueArguments.getOrNull(0) ?: return
                val resolvedName = resolveResourceName(resIdArg)
                if (resolvedName != null && resolvedName in INSET_RESOURCE_NAMES) {
                    report(context, node, resolvedName)
                }
            }
        }
    }

    private fun resolveResourceName(expression: org.jetbrains.uast.UExpression): String? {
        val resolved = expression.tryResolve() ?: return null
        if (resolved is com.intellij.psi.PsiField) {
            val name = resolved.name
            if (name in INSET_RESOURCE_NAMES) return name
        }
        return null
    }

    private fun report(context: JavaContext, node: UCallExpression, resourceName: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$resourceName` is not supported. " +
                "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
        )
    }
}