package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import org.w3c.dom.Element

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

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

        private const val ANDROID_PKG = "android"
        private const val DIMEN_TYPE = "dimen"

        // Source code methods to check
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION = "getDimension"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"

        private val RESOURCES_METHODS = setOf(
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION,
            GET_DIMENSION_PIXEL_OFFSET
        )

        // R.dimen reference pattern
        private val INSET_RESOURCE_PATTERN = Regex(
            """(?:android\.)?R\.dimen\.(${INTERNAL_INSET_RESOURCES.joinToString("|")})"""
        )
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Check all attributes for references to internal inset resources
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            checkXmlValue(context, attr.value, attr)
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkXmlValue(context, attribute.value, attribute)
    }

    private fun checkXmlValue(context: XmlContext, value: String?, attr: Attr) {
        if (value == null) return
        // Match patterns like @android:dimen/status_bar_height or @*android:dimen/status_bar_height
        val pattern = Regex("""@\*?android:dimen/(\w+)""")
        val match = pattern.find(value) ?: return
        val resourceName = match.groupValues[1]
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                buildMessage(resourceName)
            )
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = RESOURCES_METHODS.toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // We're looking for calls like:
        //   resources.getDimensionPixelSize(android.R.dimen.status_bar_height)
        // The first argument should be a reference to android.R.dimen.<inset_name>
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val argText = firstArg.asSourceString()

        // Check if the argument references one of the internal inset resources
        for (resourceName in INTERNAL_INSET_RESOURCES) {
            if (argText.contains(resourceName)) {
                // Verify it's actually referencing android.R.dimen or R.dimen
                if (argText.contains("R.dimen.$resourceName") ||
                    argText.contains("dimen.$resourceName")
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(firstArg as UElement),
                        buildMessage(resourceName)
                    )
                    return
                }
            }
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Using internal inset dimension resource `@android:dimen/$resourceName` is not " +
            "supported. The insets are dynamic values that can change while your app is visible, " +
            "and your app's window may not intersect with the system UI. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}