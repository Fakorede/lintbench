package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.LINEAR_LAYOUT) {
            return
        }

        val children = element.childElements()
        if (children.size != 2) {
            return
        }

        val imageView = children.find { it.tagName == SdkConstants.IMAGE_VIEW }
        val textView = children.find { it.tagName == SdkConstants.TEXT_VIEW }
        if (imageView == null || textView == null) {
            return
        }

        if (!imageView.hasSrcAttribute()) {
            return
        }

        val message = "This tag and its children can be replaced by a single " +
                "`<TextView/>` with a compound drawable"

        context.report(ISSUE, element, context.getElementLocation(element), message)
    }

    private fun Element.hasSrcAttribute(): Boolean {
        if (hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SRC)) {
            return true
        }
        val attrs = attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if (attr.localName == SdkConstants.ATTR_SRC) {
                return true
            }
        }
        return false
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    companion object {
        private val EXPLANATION = """
            A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
            efficiently handled as a compound drawable (a single `TextView`, using the
            `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
            attributes to draw one or more images adjacent to the text).

            If the two widgets are offset from each other with margins, this can be
            replaced with a `drawablePadding` attribute.

            There's a lint quickfix to perform this conversion in the Eclipse plugin.
        """.trimIndent()

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = EXPLANATION,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}