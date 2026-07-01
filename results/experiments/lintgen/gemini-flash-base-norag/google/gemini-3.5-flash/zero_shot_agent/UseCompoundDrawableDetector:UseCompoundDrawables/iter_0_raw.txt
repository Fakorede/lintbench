package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_TRUE
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != LINEAR_LAYOUT) return

        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) return

        val children = getChildren(element)
        if (children.size != 2) return

        var imageView: Element? = null
        var textView: Element? = null

        for (child in children) {
            val tagName = child.tagName
            if (tagName == IMAGE_VIEW || tagName == "androidx.appcompat.widget.AppCompatImageView") {
                imageView = child
            } else if (tagName == TEXT_VIEW || tagName == "androidx.appcompat.widget.AppCompatTextView") {
                textView = child
            }
        }

        if (imageView == null || textView == null) return

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) return

        if (imageView.getAttributeNS(ANDROID_URI, ATTR_CLICKABLE) == VALUE_TRUE) return

        val hasSrc = imageView.hasAttributeNS(ANDROID_URI, "src") || 
                     imageView.hasAttributeNS("http://schemas.android.com/apk/res-auto", "srcCompat")
        if (!hasSrc) return

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }

    private fun getChildren(element: Element): List<Element> {
        val list = mutableListOf<Element>()
        var curr = element.firstChild
        while (curr != null) {
            if (curr.nodeType == Node.ELEMENT_NODE) {
                list.add(curr as Element)
            }
            curr = curr.nextSibling
        }
        return list
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A LinearLayout which contains an ImageView and a TextView can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                drawableTop, drawableLeft, drawableRight and/or drawableBottom \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a drawablePadding attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
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
}