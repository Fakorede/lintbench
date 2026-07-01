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
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more 
                efficiently handled as a compound drawable (a single `TextView`, using the 
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` 
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be 
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("LinearLayout", "androidx.appcompat.widget.LinearLayoutCompat")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val childElements = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                childElements.add(node as Element)
            }
        }

        if (childElements.size != 2) {
            return
        }

        val first = childElements[0]
        val second = childElements[1]

        val firstTag = first.tagName
        val secondTag = second.tagName

        val isFirstImage = isImageView(firstTag)
        val isFirstText = isTextView(firstTag)
        val isSecondImage = isImageView(secondTag)
        val isSecondText = isTextView(secondTag)

        val imageView: Element
        if (isFirstImage && isSecondText) {
            imageView = first
        } else if (isFirstText && isSecondImage) {
            imageView = second
        } else {
            return
        }

        // If the ImageView has layout_weight, it can't be easily replaced by a compound drawable
        if (imageView.hasAttributeNS(ANDROID_URI, "layout_weight")) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a <TextView> using compound drawables"
        )
    }

    private fun isImageView(tag: String): Boolean {
        return tag == "ImageView" || tag == "androidx.appcompat.widget.AppCompatImageView" || tag.endsWith(".ImageView")
    }

    private fun isTextView(tag: String): Boolean {
        return tag == "TextView" || tag == "androidx.appcompat.widget.AppCompatTextView" || tag.endsWith(".TextView")
    }
}