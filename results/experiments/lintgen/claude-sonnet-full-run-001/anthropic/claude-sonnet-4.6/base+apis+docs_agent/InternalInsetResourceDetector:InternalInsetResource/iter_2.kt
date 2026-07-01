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
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {

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
            "taskbar_frame_height"
        )

        private const val ANDROID_DIMEN_PREFIX = "@android:dimen/"
        private const val ANDROID_PKG = "android"
        private const val DIMEN_TYPE = "dimen"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val GET_IDENTIFIER = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"
        private const val GET_DIMENSION = "getDimension"

        private const val MESSAGE =
            "The inset dimension resources are not a supported way to retrieve insets. " +
                "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."

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
                Scope.JAVA_AND_RESOURCE_FILES,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableAttributes(): Collection<String> = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        checkXmlValue(context, value, attribute)
    }

    override fun getApplicableElements(): Collection<String> = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent?.trim() ?: return
        checkXmlValue(context, text, element)
    }

    private fun checkXmlValue(context: XmlContext, value: String, node: Node) {
        if (!value.startsWith(ANDROID_DIMEN_PREFIX)) return
        val resourceName = value.removePrefix(ANDROID_DIMEN_PREFIX).trim()
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
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
        when (method.name) {
            GET_IDENTIFIER -> checkGetIdentifierCall(context, node, method)
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION_PIXEL_OFFSET,
            GET_DIMENSION -> checkGetDimensionCall(context, node, method)
        }
    }

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
                MESSAGE
            )
        }
    }

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
                MESSAGE
            )
        }
    }
}