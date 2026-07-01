package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class InternalInsetResourceDetector : ResourceXmlDetector(), Detector.UastScanner {

    companion object {
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_frame_height"
        )

        private const val ANDROID_PACKAGE = "android"

        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. \
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

        private val GET_IDENTIFIER = "getIdentifier"
        private val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"
        private val GET_DIMENSION = "getDimension"

        private val RESOURCES_CLASS = "android.content.res.Resources"
    }

    // -----------------------------------------------------------------------
    // XML scanning
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        checkXmlValue(context, attribute.value, attribute)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val text = element.textContent?.trim() ?: return
        checkXmlValue(context, text, element)
    }

    private fun checkXmlValue(context: XmlContext, value: String, node: org.w3c.dom.Node) {
        // Look for references like @android:dimen/status_bar_height
        val pattern = Regex("""@android:dimen/(\w+)""")
        val match = pattern.find(value) ?: return
        val resourceName = match.groupValues[1]
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            val location = context.getLocation(node)
            context.report(
                ISSUE,
                node,
                location,
                buildMessage(resourceName)
            )
        }
    }

    // -----------------------------------------------------------------------
    // UAST scanning
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_DIMENSION_PIXEL_SIZE,
        GET_DIMENSION_PIXEL_OFFSET,
        GET_DIMENSION,
        GET_IDENTIFIER
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) return

        val methodName = method.name

        if (methodName == GET_IDENTIFIER) {
            handleGetIdentifier(context, node)
        } else {
            handleGetDimension(context, node)
        }
    }

    private fun handleGetIdentifier(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.size < 3) return

        val nameArg = args[0]
        val typeArg = args[1]
        val packageArg = args[2]

        val evaluator = context.evaluator
        val name = ConstantEvaluator.evaluate(context, nameArg) as? String ?: return
        val type = ConstantEvaluator.evaluate(context, typeArg) as? String ?: return
        val pkg = ConstantEvaluator.evaluate(context, packageArg) as? String ?: return

        if (type == "dimen" && pkg == ANDROID_PACKAGE && name in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(name)
            )
        }
    }

    private fun handleGetDimension(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        // The first argument is the resource ID. We look for R references or
        // identifiers that resolve to android.R.dimen.<inset_resource>.
        val resIdArg = args[0]

        // Try to resolve via ResourceReference
        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, resIdArg)
        if (resourceUrl != null) {
            if (resourceUrl.type == ResourceType.DIMEN &&
                resourceUrl.`package` == ANDROID_PACKAGE &&
                resourceUrl.name in INTERNAL_INSET_RESOURCES
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    buildMessage(resourceUrl.name)
                )
            }
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Avoid using the internal `@android:dimen/$resourceName` resource; " +
            "it is not a supported API. Use `androidx.core.view.WindowInsetsCompat` " +
            "and related APIs to retrieve inset values dynamically."
    }
}