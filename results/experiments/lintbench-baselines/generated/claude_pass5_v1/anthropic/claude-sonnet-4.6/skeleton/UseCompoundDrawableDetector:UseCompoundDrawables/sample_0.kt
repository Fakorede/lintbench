package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_VERTICAL
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
            explanation =
                "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                "efficiently handled as a compound drawable (a single TextView, using the " +
                "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                "attributes to draw one or more images adjacent to the text).\n" +
                "\n" +
                "If the two widgets are offset from each other with margins, this can be " +
                "replaced with a `drawablePadding` attribute.\n" +
                "\n" +
                "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        // Must be a LinearLayout
        // Check that it does not have a background (compound drawables don't support that)
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        // Collect the child elements (ignore whitespace text nodes)
        val children = getChildElements(element)

        // Must have exactly 2 children
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        // One must be an ImageView and the other a TextView
        val hasImageAndText =
            (first.tagName == IMAGE_VIEW && second.tagName == TEXT_VIEW) ||
            (first.tagName == TEXT_VIEW && second.tagName == IMAGE_VIEW)

        if (!hasImageAndText) {
            return
        }

        // Determine which is the ImageView and which is the TextView
        val imageView = if (first.tagName == IMAGE_VIEW) first else second
        val textView = if (first.tagName == TEXT_VIEW) first else second

        // The ImageView must not have a layout_weight attribute set
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        // The TextView must not have a layout_weight attribute set
        if (textView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        // The orientation must be horizontal or vertical (default is horizontal)
        // We just check it's not something unusual; both horizontal and vertical are fine
        // for compound drawables.

        // Check that the ImageView does not have a background
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable",
        )
    }

    /**
     * Returns the child [Element] nodes of the given element, skipping non-element nodes
     * such as text and comment nodes.
     */
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