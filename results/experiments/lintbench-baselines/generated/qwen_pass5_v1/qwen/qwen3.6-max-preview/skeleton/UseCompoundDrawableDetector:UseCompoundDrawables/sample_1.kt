package com.android.tools.lint.checks

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
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more efficiently handled as a compound drawable (a single TextView, using the `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more images adjacent to the text).\n\nIf the two widgets are offset from each other with margins, this can be replaced with a `drawablePadding` attribute.",
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
        val children = element.childNodes
        var elementCount = 0
        var hasImageView = false
        var hasTextView = false

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                elementCount++
                val tag = (child as Element).tagName
                if (tag == "ImageView" || tag.endsWith(".ImageView")) {
                    hasImageView = true
                } else if (tag == "TextView" || tag.endsWith(".TextView")) {
                    hasTextView = true
                }
            }
        }

        if (elementCount == 2 && hasImageView && hasTextView) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This LinearLayout can be replaced with a TextView using compound drawables"
            )
        }
    }
}