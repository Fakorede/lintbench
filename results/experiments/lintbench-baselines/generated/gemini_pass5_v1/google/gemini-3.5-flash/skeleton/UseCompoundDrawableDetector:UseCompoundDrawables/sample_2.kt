package com.android.tools.lint.checks

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
        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                    "efficiently handled as a compound drawable (a single `TextView`, using the " +
                    "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                    "attributes to draw one or more images adjacent to the text).\n\n" +
                    "If the two widgets are offset from each other with margins, this can be " +
                    "replaced with a `drawablePadding` attribute.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        val nodeList = element.childNodes
        for (i in 0 until nodeList.length) {
            val child = nodeList.item(i)
            if (child is Element) {
                children.add(child)
            }
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]
        val firstTag = first.tagName
        val secondTag = second.tagName

        var imageView: Element? = null
        var textView: Element? = null

        if (firstTag == "ImageView") {
            imageView = first
        } else if (firstTag == "TextView") {
            textView = first
        }

        if (secondTag == "ImageView") {
            imageView = second
        } else if (secondTag == "TextView") {
            textView = second
        }

        if (imageView != null && textView != null && imageView != textView) {
            val androidUri = "http://schemas.android.com/apk/res/android"
            if (imageView.hasAttributeNS(androidUri, "layout_weight")) {
                return
            }
            if (imageView.hasAttributeNS(androidUri, "clickable")) {
                return
            }
            if (imageView.hasAttributeNS(androidUri, "focusable")) {
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
}