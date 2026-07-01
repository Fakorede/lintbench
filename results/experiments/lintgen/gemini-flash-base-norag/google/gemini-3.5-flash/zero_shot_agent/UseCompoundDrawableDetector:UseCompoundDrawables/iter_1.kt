package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
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

        if (element.hasAttributeNS(ANDROID_URI, "background")) return

        val children = getChildren(element)
        if (children.size != 2) return

        val child1 = children[0]
        val child2 = children[1]

        val tag1 = child1.tagName
        val tag2 = child2.tagName

        val isImage1 = tag1 == IMAGE_VIEW || tag1 == "ImageView" || tag1 == "androidx.appcompat.widget.AppCompatImageView"
        val isText1 = tag1 == TEXT_VIEW || tag1 == "TextView" || tag1 == "androidx.appcompat.widget.AppCompatTextView"
        val isImage2 = tag2 == IMAGE_VIEW || tag2 == "ImageView" || tag2 == "androidx.appcompat.widget.AppCompatImageView"
        val isText2 = tag2 == TEXT_VIEW || tag2 == "TextView" || tag2 == "androidx.appcompat.widget.AppCompatTextView"

        val imageView: Element
        val textView: Element

        if (isImage1 && isText2) {
            imageView = child1
            textView = child2
        } else if (isImage2 && isText1) {
            imageView = child2
            textView = child1
        } else {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, "clickable") ||
            imageView.hasAttributeNS(ANDROID_URI, "focusable") ||
            imageView.hasAttributeNS(ANDROID_URI, "onClick")
        ) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, "layout_weight")) {
            return
        }

        val hasSrc = imageView.hasAttributeNS(ANDROID_URI, "src") ||
                imageView.hasAttributeNS(AUTO_URI, "srcCompat")
        if (!hasSrc) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
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
                Scope.LAYOUT_RESOURCE_FILE_SCOPE
            )
        )
    }
}