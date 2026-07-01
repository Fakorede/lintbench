package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.SdkConstants.VALUE_HORIZONTAL
import com.android.SdkConstants.VALUE_VERTICAL
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
            child = child.nextSibling
        }

        if (children.size != 2) return

        val imageView = children.find { it.tagName == TAG_IMAGE_VIEW }
        val textView = children.find { it.tagName == TAG_TEXT_VIEW }

        if (imageView == null || textView == null) return

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        val isHorizontal = orientation.isEmpty() || orientation == VALUE_HORIZONTAL
        val isVertical = orientation == VALUE_VERTICAL

        if (!isHorizontal && !isVertical) return

        fun isWrapContent(e: Element, attr: String): Boolean {
            return e.getAttributeNS(ANDROID_URI, attr) == VALUE_WRAP_CONTENT
        }

        if (!isWrapContent(imageView, ATTR_LAYOUT_WIDTH) || !isWrapContent(imageView, ATTR_LAYOUT_HEIGHT)) return
        if (!isWrapContent(textView, ATTR_LAYOUT_WIDTH) || !isWrapContent(textView, ATTR_LAYOUT_HEIGHT)) return

        val hasSrc = imageView.hasAttributeNS(ANDROID_URI, ATTR_SRC)
        val hasText = textView.hasAttributeNS(ANDROID_URI, ATTR_TEXT)

        if (!hasSrc || !hasText) return

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = "A LinearLayout which contains an ImageView and a TextView can be more efficiently handled as a compound drawable (a single TextView, using the drawableTop, drawableLeft, drawableRight and/or drawableBottom attributes to draw one or more images adjacent to the text).\n\n" +
                    "If the two widgets are offset from each other with margins, this can be replaced with a drawablePadding attribute.\n\n" +
                    "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}