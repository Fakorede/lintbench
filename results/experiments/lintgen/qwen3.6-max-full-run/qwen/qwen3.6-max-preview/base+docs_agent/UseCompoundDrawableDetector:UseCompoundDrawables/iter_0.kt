package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = getDirectChildElements(element)
        if (children.size != 2) return

        val tag1 = children[0].tagName
        val tag2 = children[1].tagName

        val isImage1 = isImageView(tag1)
        val isText1 = isTextView(tag1)
        val isImage2 = isImageView(tag2)
        val isText2 = isTextView(tag2)

        if ((isImage1 && isText2) || (isText1 && isImage2)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by one <TextView/> and a compound drawable"
            )
        }
    }

    private fun getDirectChildElements(element: Element): List<Element> {
        val result = mutableListOf<Element>()
        var node = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
            node = node.nextSibling
        }
        return result
    }

    private fun isImageView(tag: String): Boolean {
        return tag == SdkConstants.IMAGE_VIEW || tag.endsWith(".${SdkConstants.IMAGE_VIEW}")
    }

    private fun isTextView(tag: String): Boolean {
        return tag == SdkConstants.TEXT_VIEW || tag.endsWith(".${SdkConstants.TEXT_VIEW}")
    }
}