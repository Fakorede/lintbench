package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
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
import org.w3c.dom.Element

/**
 * Detector that flags usage of internal inset dimension resources such as
 * `@android:dimen/status_bar_height`, `@android:dimen/navigation_bar_height`, etc.
 *
 * These are internal platform resources and not a supported API for retrieving
 * insets. Apps should use `androidx.core.view.WindowInsetsCompat` instead.
 */
class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {

        /** The set of known internal inset dimension resource names */
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height_car_mode",
            "navigation_bar_width_car_mode",
            "system_bar_height",
            "action_bar_height",
            "taskbar_frame_height"
        )

        private const val ANDROID_DIMEN_PREFIX = "@android:dimen/"
        private const val ANDROID_PKG = "android"
        private const val DIMEN_TYPE = "dimen"

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
                Scope.JAVA_AND_RESOURCE_FILES,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val GET_IDENTIFIER = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"
        private const val GET_DIMENSION = "getDimension"
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        checkXmlValue(context, value, attribute)
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of elements (e.g. <item>@android:dimen/status_bar_height</item>)
        val text = element.textContent?.trim() ?: return
        checkXmlValue(context, text, element)
    }

    private fun checkXmlValue(context: XmlContext, value: String, node: org.w3c.dom.Node) {
        if (!value.startsWith(ANDROID_DIMEN_PREFIX)) return
        val resourceName = value.removePrefix(ANDROID_DIMEN_PREFIX).trim()
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(resourceName)
            )
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_IDENTIFIER,
        GET_DIMENSION_PIXEL_SIZE,
        GET_DIMENSION_PIXEL_OFFSET,
        GET_DIMENSION
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        when (methodName) {
            GET_IDENTIFIER -> checkGetIdentifierCall(context, node, method)
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION_PIXEL_OFFSET,
            GET_DIMENSION -> checkGetDimensionCall(context, node, method)
        }
    }

    /**
     * Checks calls like:
     *   resources.getIdentifier("status_bar_height", "dimen", "android")
     */
    private fun checkGetIdentifierCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, RESOURCES_CLASS)) return

        val args = node.valueArguments
        if (args.size < 3) return

        val nameArg = args[0].evaluate() as? String ?: return
        val typeArg = args[1].evaluate() as? String ?: return
        val pkgArg = args[2].evaluate() as? String ?: return

        if (typeArg == DIMEN_TYPE && pkgArg == ANDROID_PKG && nameArg in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(nameArg)
            )
        }
    }

    /**
     * Checks calls like:
     *   resources.getDimensionPixelSize(android.R.dimen.status_bar_height)
     *
     * We look at the resolved field reference passed as the resource ID argument.
     */
    private fun checkGetDimensionCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, RESOURCES_CLASS)) return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val resIdArg = args[0]
        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, resIdArg) ?: return

        val pkg = resourceUrl.namespace
        val type = resourceUrl.type
        val name = resourceUrl.name

        if (pkg == ANDROID_PKG &&
            type == ResourceType.DIMEN &&
            name in INTERNAL_INSET_RESOURCES
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(name)
            )
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Avoid using the internal resource `@android:dimen/$resourceName` to retrieve " +
            "inset values. These are internal platform resources and not a supported API; " +
            "the values are dynamic and may not reflect your app's actual insets. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}