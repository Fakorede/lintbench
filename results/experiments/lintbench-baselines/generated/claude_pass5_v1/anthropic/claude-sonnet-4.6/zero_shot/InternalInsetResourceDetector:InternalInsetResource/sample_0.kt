package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import com.android.SdkConstants
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

/**
 * Detector that flags usage of internal inset dimension resources like
 * `@android:dimen/status_bar_height`, `@android:dimen/navigation_bar_height`, etc.
 */
class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height_landscape",
            "navigation_bar_interaction_mode",
            "navigation_bar_frame_height",
            "system_bar_height",
            "action_bar_height",
            "window_content_overlay"
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

        private const val ANDROID_PKG = "android"
        private const val DIMEN_TYPE = "dimen"

        // Methods used to look up resources programmatically
        private val RESOURCE_LOOKUP_METHODS = setOf(
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
            "getIdentifier"
        )
    }

    // ---- XmlScanner ----

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        checkXmlValue(context, attribute, value)
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of elements like <item> in resources
        val textContent = element.textContent?.trim() ?: return
        if (textContent.isNotEmpty()) {
            checkXmlValue(context, element, textContent)
        }
    }

    private fun checkXmlValue(context: XmlContext, node: org.w3c.dom.Node, value: String) {
        // Match patterns like @android:dimen/status_bar_height or @*android:dimen/status_bar_height
        val pattern = Regex("""@\*?android:dimen/(\w+)""")
        val match = pattern.find(value) ?: return
        val resourceName = match.groupValues[1]

        if (resourceName in INTERNAL_INSET_RESOURCES) {
            val location = if (node is Attr) {
                context.getValueLocation(node)
            } else {
                context.getLocation(node)
            }
            context.report(
                ISSUE,
                node,
                location,
                buildMessage(resourceName)
            )
        }
    }

    // ---- SourceCodeScanner ----

    override fun getApplicableMethodNames(): List<String> {
        return RESOURCE_LOOKUP_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "getDimensionPixelSize", "getDimensionPixelOffset", "getDimension" -> {
                // Check if the argument is a reference to an internal inset resource
                // These methods take a resource ID (int), so we need to check if it's
                // referencing an internal resource via R.dimen or android.R.dimen
                checkDimensionMethodCall(context, node, method)
            }
            "getIdentifier" -> {
                // Check if the name argument is an internal inset resource
                checkGetIdentifierCall(context, node)
            }
        }
    }

    private fun checkDimensionMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]

        // Use ResourceEvaluator to check if the argument references an android internal resource
        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, firstArg) ?: return

        if (resourceUrl.`package` == ANDROID_PKG &&
            resourceUrl.type == ResourceType.DIMEN &&
            resourceUrl.name in INTERNAL_INSET_RESOURCES
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(firstArg),
                buildMessage(resourceUrl.name)
            )
        }
    }

    private fun checkGetIdentifierCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size < 3) return

        // getIdentifier(name, defType, defPackage)
        val nameArg = arguments[0]
        val defTypeArg = arguments[1]
        val defPackageArg = arguments[2]

        val nameValue = nameArg.evaluate() as? String ?: return
        val defTypeValue = defTypeArg.evaluate() as? String
        val defPackageValue = defPackageArg.evaluate() as? String

        if (defTypeValue == DIMEN_TYPE &&
            defPackageValue == ANDROID_PKG &&
            nameValue in INTERNAL_INSET_RESOURCES
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(nameValue)
            )
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Using internal inset resource `@android:dimen/$resourceName` is not supported. " +
                "The insets are dynamic values that can change while your app is visible. " +
                "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}