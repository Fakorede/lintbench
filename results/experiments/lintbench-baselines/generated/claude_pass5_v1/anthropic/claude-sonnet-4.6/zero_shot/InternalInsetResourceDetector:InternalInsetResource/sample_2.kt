package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height_landscape",
            "navigation_bar_interaction_mode",
            "navigation_bar_frame_height",
            "navigation_bar_gesture_hint_height",
        )

        private const val MESSAGE =
            "Using internal inset dimension resource. The internal inset dimension resources " +
                "are not a supported way to retrieve the relevant insets for your application. " +
                "The insets are dynamic values that can change while your app is visible, and " +
                "your app's window may not intersect with the system UI. To get the relevant " +
                "value for your app and listen to updates, use " +
                "`androidx.core.view.WindowInsetsCompat` and related APIs."

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
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )

        // Resource identifiers class names
        private const val ANDROID_R_DIMEN = "android.R.dimen"
        private const val R_DIMEN = "R.dimen"
    }

    // ---- XmlScanner ----

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        checkResourceReference(context, value, attribute)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // handled via attributes
    }

    private fun checkResourceReference(context: XmlContext, value: String, attribute: Attr) {
        if (!value.startsWith("@android:dimen/") && !value.startsWith("@*android:dimen/")) {
            return
        }
        val resourceName = value.substringAfterLast("/")
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                MESSAGE
            )
        }
    }

    // ---- SourceCodeScanner ----

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getIdentifier",
        "getDimensionPixelSize",
        "getDimensionPixelOffset",
        "getDimension"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        when (methodName) {
            "getIdentifier" -> {
                // Resources.getIdentifier("status_bar_height", "dimen", "android")
                if (containingClass == "android.content.res.Resources") {
                    checkGetIdentifierCall(context, node)
                }
            }
            "getDimensionPixelSize", "getDimensionPixelOffset", "getDimension" -> {
                // Resources.getDimensionPixelSize(android.R.dimen.status_bar_height)
                if (containingClass == "android.content.res.Resources") {
                    checkResourceIdArgument(context, node)
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return INTERNAL_INSET_RESOURCES.toList()
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // Not used for field references
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                checkQualifiedReference(context, node)
            }
        }
    }

    private fun checkQualifiedReference(context: JavaContext, node: UQualifiedReferenceExpression) {
        val text = node.asSourceString()
        // Check for patterns like android.R.dimen.status_bar_height or R.dimen.status_bar_height
        if (!text.contains("dimen")) return

        for (resourceName in INTERNAL_INSET_RESOURCES) {
            if (text.endsWith(".$resourceName") || text.endsWith(".$resourceName)")) {
                // Check if it's referencing android R dimen
                if (text.contains("android.R.dimen") || isAndroidRDimenReference(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                    return
                }
            }
        }
    }

    private fun isAndroidRDimenReference(node: UQualifiedReferenceExpression): Boolean {
        val text = node.asSourceString()
        return text.startsWith("android.R.dimen.") || 
               (text.startsWith("R.dimen.") && isAndroidResource(node))
    }

    private fun isAndroidResource(node: UElement): Boolean {
        // Heuristic: if it's just R.dimen.xxx it could be the app's own R,
        // we focus on android.R.dimen.xxx
        return false
    }

    private fun checkGetIdentifierCall(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.size < 3) return

        val nameArg = args[0]
        val typeArg = args[1]
        val packageArg = args[2]

        val nameValue = getStringValue(nameArg) ?: return
        val typeValue = getStringValue(typeArg) ?: return
        val packageValue = getStringValue(packageArg) ?: return

        if (typeValue == "dimen" && packageValue == "android" && nameValue in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }

    private fun checkResourceIdArgument(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val argText = firstArg.asSourceString()

        for (resourceName in INTERNAL_INSET_RESOURCES) {
            if (argText.contains("android.R.dimen.$resourceName") ||
                argText == "android.R.dimen.$resourceName"
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
                return
            }
        }
    }

    private fun getStringValue(expression: UExpression): String? {
        val evaluated = expression.evaluate()
        if (evaluated is String) return evaluated

        // Try to get the literal string value from source
        val source = expression.asSourceString()
        if (source.startsWith("\"") && source.endsWith("\"")) {
            return source.substring(1, source.length - 1)
        }
        return null
    }
}