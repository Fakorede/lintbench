package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SCALE_TYPE
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

    companion object {
        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

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
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        // Collect child elements (skip text/whitespace nodes)
        val children = getChildElements(element)

        // Must have exactly 2 children
        if (children.size != 2) return

        // One must be an ImageView and one must be a TextView
        val hasImageView = children.any { it.tagName == IMAGE_VIEW }
        val hasTextView = children.any { it.tagName == TEXT_VIEW }

        if (!hasImageView || !hasTextView) return

        // If the LinearLayout has a background, we can't easily convert it
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) return

        // Check that the ImageView doesn't have a special scale type that would
        // prevent conversion (anything other than the default is problematic)
        val imageView = children.first { it.tagName == IMAGE_VIEW }
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)) return

        // Check that neither child has layout_weight set (weight complicates conversion)
        for (child in children) {
            if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and " +
                "a compound drawable",
        )
    }

    private fun getChildElements(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
            child = child.nextSibling
        }
        return children
    }
}