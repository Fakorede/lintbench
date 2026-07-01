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
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun visitDocument(context: XmlContext, document: Document) {
        visitNode(context, document)
    }

    private fun visitNode(context: XmlContext, node: Node) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val value = attr.value ?: continue
                checkValue(context, attr, value)
            }
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.TEXT_NODE) {
                    val text = child.nodeValue?.trim()
                    if (!text.isNullOrEmpty()) {
                        checkValue(context, child, text)
                    }
                }
            }
        }
        var child = node.firstChild
        while (child != null) {
            visitNode(context, child)
            child = child.nextSibling
        }
    }

    private fun checkValue(context: XmlContext, node: Node, value: String) {
        val matcher = RESOURCE_PATTERN.matcher(value)
        while (matcher.find()) {
            val name = matcher.group(1) ?: continue
            if (name in INSET_RESOURCES) {
                val location = if (node is Attr) context.getValueLocation(node) else context.getLocation(node)
                context.report(ISSUE, location, MESSAGE)
            }
        }
    }

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (type == ResourceType.DIMEN && isFramework && name in INSET_RESOURCES) {
            context.report(ISSUE, context.getLocation(node), MESSAGE)
        }
    }

    companion object {
        private val INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width"
        )

        private val RESOURCE_PATTERN =
            Pattern.compile("@\\*?android:dimen/(${INSET_RESOURCES.joinToString("|")})\\b")

        private const val MESSAGE =
            "Using internal inset dimension resource. To get the relevant value for your app and listen to updates, use androidx.core.view.WindowInsetsCompat and related APIs."

        @JvmField
        val ISSUE = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}