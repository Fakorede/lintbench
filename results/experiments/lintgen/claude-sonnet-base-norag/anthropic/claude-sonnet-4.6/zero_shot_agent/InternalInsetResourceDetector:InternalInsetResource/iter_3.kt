package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import java.util.EnumSet

/**
 * Detector that flags usage of internal inset dimension resources like
 * `@android:dimen/status_bar_height`, `@android:dimen/navigation_bar_height`, etc.
 */
class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "navigation_bar_height_landscape",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_interaction_height",
            "navigation_bar_frame_height",
            "system_bar_height"
        )

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
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )

        private const val MESSAGE =
            "Using internal inset dimension resources is not a supported way to retrieve " +
                "insets for your application. Use `androidx.core.view.WindowInsetsCompat` " +
                "and related APIs instead."

        private const val RESOURCES_CLASS = "android.content.res.Resources"

        private val GET_DIMEN_METHODS = setOf(
            "getDimension",
            "getDimensionPixelOffset",
            "getDimensionPixelSize"
        )

        private val RESOURCE_REFERENCE_PATTERN = Regex("""@\*?android:dimen/(\w+)""")
    }

    // -------------------------------------------------------------------------
    // XML scanning
    // -------------------------------------------------------------------------

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val match = RESOURCE_REFERENCE_PATTERN.find(value) ?: return
        val resourceName = match.groupValues[1]
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                MESSAGE
            )
        }
    }

    override fun getApplicableElements(): Collection<String>? = null

    // -------------------------------------------------------------------------
    // Java/Kotlin scanning
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "getDimension",
            "getDimensionPixelOffset",
            "getDimensionPixelSize",
            "getIdentifier"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        when {
            methodName == "getIdentifier" && containingClass == RESOURCES_CLASS -> {
                checkGetIdentifierCall(context, node)
            }
            methodName in GET_DIMEN_METHODS && containingClass == RESOURCES_CLASS -> {
                checkGetDimenCall(context, node)
            }
        }
    }

    private fun checkGetIdentifierCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size < 3) return

        val nameArg = arguments[0]
        val typeArg = arguments[1]
        val packageArg = arguments[2]

        val nameValue = nameArg.evaluate() as? String ?: return
        val typeValue = typeArg.evaluate() as? String ?: return
        val packageValue = packageArg.evaluate() as? String ?: return

        if (typeValue == "dimen" && packageValue == "android" && nameValue in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }

    private fun checkGetDimenCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val resIdArg = arguments[0]

        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, resIdArg) ?: return

        val pkg = resourceUrl.namespace
        val name = resourceUrl.name
        val type = resourceUrl.type

        if (type == ResourceType.DIMEN &&
            (pkg == "android" || pkg == "com.android.internal") &&
            name in INTERNAL_INSET_RESOURCES
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }
}