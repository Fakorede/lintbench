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
import org.jetbrains.uast.evaluateString

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "navigation_bar_height_car_mode",
            "navigation_bar_width_car_mode",
            "status_bar_height_portrait",
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

        private val APPLICABLE_METHOD_NAMES = listOf(
            "getIdentifier",
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "getIdentifier" -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS)
                ) {
                    return
                }
                val nameArg = node.valueArguments.firstOrNull() ?: return
                val name = resolveResourceName(nameArg) ?: return
                if (name in INSET_RESOURCE_NAMES) {
                    report(context, node, name)
                }
            }

            "getDimensionPixelSize", "getDimensionPixelOffset", "getDimension" -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS)
                ) {
                    return
                }
                // Check if the resource ID argument comes from getIdentifier with a known inset name,
                // or if it references a known internal resource directly via R.dimen
                val resIdArg = node.valueArguments.firstOrNull() ?: return
                val resourceName = resolveResourceNameFromIdArg(resIdArg) ?: return
                if (resourceName in INSET_RESOURCE_NAMES) {
                    report(context, node, resourceName)
                }
            }
        }
    }

    private fun resolveResourceName(expression: UExpression): String? {
        val evaluated = expression.evaluateString()
        if (evaluated != null) {
            return evaluated
        }
        // Try to get the source text
        val sourcePsi = expression.sourcePsi ?: return null
        val text = sourcePsi.text?.trim('"') ?: return null
        return if (text in INSET_RESOURCE_NAMES) text else null
    }

    private fun resolveResourceNameFromIdArg(expression: UExpression): String? {
        val sourcePsi = expression.sourcePsi ?: return null
        val text = sourcePsi.text ?: return null
        // Check if the text references a known inset resource name via R.dimen.<name>
        for (name in INSET_RESOURCE_NAMES) {
            if (text.endsWith(".$name") || text == name) {
                return name
            }
        }
        return null
    }

    private fun report(context: JavaContext, node: UCallExpression, resourceName: String) {
        val message = "Avoid using internal inset dimension resource `$resourceName`. " +
            "These are not a supported way to retrieve insets for your application. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }
}