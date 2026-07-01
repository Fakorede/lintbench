package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import com.android.SdkConstants
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import com.android.tools.lint.detector.api.LintFix

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
                Scope.JAVA_AND_RESOURCE_FILES,
                Scope.JAVA_FILE_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val MESSAGE =
            "Using internal inset dimension resources is not a supported way to retrieve " +
                "insets for your application. Use `androidx.core.view.WindowInsetsCompat` " +
                "and related APIs instead."

        private val GET_IDENTIFIER_METHODS = setOf(
            "getIdentifier"
        )

        private val RESOURCES_CLASS = "android.content.res.Resources"
        private val GET_DIMEN_METHODS = setOf(
            "getDimension",
            "getDimensionPixelOffset",
            "getDimensionPixelSize"
        )
    }

    // -------------------------------------------------------------------------
    // XML scanning
    // -------------------------------------------------------------------------

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        checkResourceReference(value) { message ->
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                message
            )
        }
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of elements (e.g., <item> elements in dimen files)
        val textContent = element.textContent?.trim() ?: return
        checkResourceReference(textContent) { message ->
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                message
            )
        }
    }

    private fun checkResourceReference(value: String, report: (String) -> Unit) {
        // Match patterns like @android:dimen/status_bar_height or @*android:dimen/status_bar_height
        val pattern = Regex("""@\*?android:dimen/(\w+)""")
        val match = pattern.find(value) ?: return
        val resourceName = match.groupValues[1]
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            report(MESSAGE)
        }
    }

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
        if (arguments.isEmpty()) return

        // getIdentifier(String name, String defType, String defPackage)
        // We look for calls like getIdentifier("status_bar_height", "dimen", "android")
        val nameArg = arguments.getOrNull(0) ?: return
        val typeArg = arguments.getOrNull(1) ?: return
        val packageArg = arguments.getOrNull(2) ?: return

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

        // Try to resolve the resource reference
        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, resIdArg) ?: return

        if (resourceUrl.type == ResourceType.DIMEN &&
            resourceUrl.`package` == "android" &&
            resourceUrl.name in INTERNAL_INSET_RESOURCES
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