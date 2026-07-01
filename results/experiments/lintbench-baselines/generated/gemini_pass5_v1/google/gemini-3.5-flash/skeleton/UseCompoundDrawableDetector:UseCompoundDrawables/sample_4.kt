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
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
                """.trimIndent(),
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
        val childNodes = element.childNodes
        val children = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                children.add(node)
            }
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val firstTag = first.tagName
        val secondTag = second.tagName

        val imageView = if (firstTag == "ImageView") first else if (secondTag == "ImageView") second else null
        val textView = if (firstTag == "TextView") first else if (secondTag == "TextView") second else null

        if (imageView == null || textView == null) {
            return
        }

        val ns = "http://schemas.android.com/apk/res/android"
        
        // Ignore if the ImageView has a layout_weight
        if (imageView.hasAttributeNS(ns, "layout_weight")) {
            return
        }

        // Ignore if the ImageView is gone
        if (imageView.getAttributeNS(ns, "visibility") == "gone") {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by a `<TextView/>` and a compound drawable"
        )
    }
}