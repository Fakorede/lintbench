package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

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

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val children = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val item = childNodes.item(i)
            if (item is Element) {
                children.add(item)
            }
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val imageView: Element
        val textView: Element

        if (first.tagName == SdkConstants.IMAGE_VIEW && second.tagName == SdkConstants.TEXT_VIEW) {
            imageView = first
            textView = second
        } else if (first.tagName == SdkConstants.TEXT_VIEW && second.tagName == SdkConstants.IMAGE_VIEW) {
            textView = first
            imageView = second
        } else {
            return
        }

        // Ignore if the ImageView is clickable
        if (imageView.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CLICKABLE) &&
            imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CLICKABLE) == SdkConstants.VALUE_TRUE) {
            return
        }

        // Ignore if the ImageView has a background (which might be a selector/ripple for clicks)
        if (imageView.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)) {
            return
        }

        // Ignore if the ImageView has a layout weight
        if (imageView.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }
}