package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childElements
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val imageFirst = first.tagName == SdkConstants.IMAGE_VIEW &&
                second.tagName == SdkConstants.TEXT_VIEW
        val imageSecond = first.tagName == SdkConstants.TEXT_VIEW &&
                second.tagName == SdkConstants.IMAGE_VIEW

        if (!imageFirst && !imageSecond) {
            return
        }

        val imageView = if (imageFirst) first else second
        if (imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SRC).isEmpty()) {
            return
        }

        val textView = if (imageFirst) second else first
        if (textView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT).isEmpty()) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This `LinearLayout` can be replaced with a single `TextView` using compound drawables"
        )
    }

    private val Element.childElements: List<Element>
        get() {
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
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single `TextView`, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes \
                to draw one or more images adjacent to the text). If the two widgets are offset \
                from each other with margins, this can be replaced with a `drawablePadding` attribute.
            """,
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}