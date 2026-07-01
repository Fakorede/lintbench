package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import org.w3c.dom.Document
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
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        checkNode(context, document.documentElement)
    }

    private fun checkNode(context: XmlContext, element: Element?) {
        element ?: return
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            val value = attr.value ?: continue
            checkXmlValue(context, value, attr)
        }
        // Also check text content for dimen references in value elements
        val textContent = element.textContent?.trim() ?: ""
        if (element.childNodes.length == 1 && element.firstChild?.nodeType == org.w3c.dom.Node.TEXT_NODE) {
            checkXmlValue(context, textContent, element)
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkNode(context, child)
            }
        }
    }

    private fun checkXmlValue(context: XmlContext, value: String, node: Any) {
        // Match patterns like @android:dimen/status_bar_height or @*android:dimen/status_bar_height
        val pattern = Regex("""@\*?android:dimen/(\w+)""")
        val matches = pattern.findAll(value)
        for (match in matches) {
            val resourceName = match.groupValues[1]
            if (resourceName in INTERNAL_INSET_RESOURCES) {
                when (node) {
                    is Attr -> context.report(
                        ISSUE,
                        node,
                        context.getValueLocation(node),
                        buildMessage(resourceName)
                    )
                    is Element -> context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        buildMessage(resourceName)
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (isFramework && type == ResourceType.DIMEN && name in INTERNAL_INSET_RESOURCES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(name)
            )
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Using internal inset dimension resource `@android:dimen/$resourceName` is not " +
            "supported. The insets are dynamic values that can change while your app is visible, " +
            "and your app's window may not intersect with the system UI. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}